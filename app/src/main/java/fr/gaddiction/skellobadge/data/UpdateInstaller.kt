package fr.gaddiction.skellobadge.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Télécharge la nouvelle version et la confie à l'installateur d'Android, sans passer
 * par le navigateur ni le gestionnaire de fichiers.
 *
 * Trois garanties avant de proposer quoi que ce soit à l'installation :
 * - la taille reçue est celle qu'annonce GitHub, ce qui écarte un téléchargement tronqué ;
 * - l'empreinte SHA-256 est celle qu'annonce GitHub, ce qui écarte un fichier altéré ;
 * - Android, enfin, refuse toute mise à jour signée d'une autre clé que la version
 *   installée : même un APK qui passerait les deux contrôles précédents ne pourrait pas
 *   remplacer l'application.
 *
 * L'installation passe par une session [PackageInstaller], l'API que recommande Android
 * depuis l'abandon d'ACTION_INSTALL_PACKAGE. L'utilisateur confirme toujours dans la
 * fenêtre du système.
 */
class UpdateInstaller(private val context: Context) {

    sealed interface State {
        data object Idle : State

        /** Android exige d'autoriser l'application à installer d'autres APK, une fois. */
        data class NeedsPermission(val release: UpdateChecker.Release) : State

        /** Progression entre 0 et 1, ou nulle quand la taille totale est inconnue. */
        data class Downloading(val progress: Float?) : State

        /** APK confié à Android : la fenêtre de confirmation du système est affichée. */
        data object Installing : State

        data class Failed(val reason: String) : State
    }

    /** L'utilisateur a-t-il autorisé cette application à installer des mises à jour ? */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Écran système où accorder cette autorisation, pour cette seule application. */
    fun permissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName),
        )

    /** Télécharge l'APK de la release et le vérifie. Lève en cas d'échec. */
    suspend fun download(
        release: UpdateChecker.Release,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: error("Aucun APK joint à cette version")
        val target = apkFile(context).apply {
            parentFile?.mkdirs()
            delete()
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // GitHub redirige vers son stockage de fichiers, toujours en HTTPS.
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "SkelloBadge (Android)")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Téléchargement refusé (HTTP $code)")
            val total = release.apkSize ?: connection.contentLengthLong.takeIf { it > 0 }

            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read

                        // Un rafraîchissement par point de pourcentage suffit à l'écran.
                        val percent = total?.let { (received * 100 / it).toInt() } ?: -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(total?.let { (received.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally {
            connection.disconnect()
        }

        release.apkSize?.let { expected ->
            if (received != expected) {
                target.delete()
                error("Téléchargement incomplet, réessaie")
            }
        }
        release.apkSha256?.let { expected ->
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                target.delete()
                error("Le fichier reçu ne correspond pas à la version publiée")
            }
        }
        target
    }

    /**
     * Confie l'APK à Android. La suite — fenêtre de confirmation, réussite ou refus —
     * arrive de façon asynchrone dans [UpdateInstallReceiver].
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val status = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_STATUS)
                // Mutable : c'est le système qui y ajoute l'état de l'installation.
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    status,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
            report(State.Installing)
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw t
        }
    }

    companion object {
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024

        /**
         * État partagé par tout le processus : l'issue de l'installation parvient à un
         * récepteur de diffusion, pas à l'écran qui l'a lancée.
         */
        private val _state = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> = _state.asStateFlow()

        fun report(state: State) {
            _state.value = state
        }

        private fun apkFile(context: Context): File =
            File(File(context.cacheDir, "updates"), "update.apk")

        /** Après la mise à jour, l'APK n'a plus d'usage : dix mégaoctets de rendus. */
        fun cleanUp(context: Context) {
            runCatching { apkFile(context).delete() }
        }
    }
}
