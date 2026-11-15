package fr.gaddiction.skellobadge.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import fr.gaddiction.skellobadge.domain.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cherche une version plus récente parmi les releases GitHub du dépôt.
 *
 * L'application s'installe hors du Play Store : sans ce contrôle, une correction publiée
 * n'atteindrait personne, l'application étant faite pour être installée puis oubliée.
 *
 * Seule la dernière release *publiée* compte : l'API `releases/latest` écarte d'elle-même
 * brouillons et préversions, de sorte qu'une version en test n'est jamais proposée.
 */
class UpdateChecker(private val context: Context) {

    data class Release(
        /** Numéro sans le préfixe `v` de l'étiquette : celui qu'annonce l'application. */
        val version: String,
        /** Page de la release, avec ses notes : repli si l'APK n'est pas joint. */
        val pageUrl: String,
        /** Lien direct vers l'APK joint à la release, s'il y en a un. */
        val apkUrl: String?,
        /** Notes de version, telles que générées par GitHub. */
        val notes: String,
    ) {
        /** Ce qu'ouvre « Mettre à jour » : l'APK de préférence, la page sinon. */
        val downloadUrl: String get() = apkUrl ?: pageUrl
    }

    sealed interface Result {
        data class Available(val release: Release, val installed: String) : Result
        data class UpToDate(val installed: String) : Result
        data class Failed(val reason: String) : Result
    }

    /** Version installée, telle qu'inscrite dans l'APK par la CI de publication. */
    fun installedVersion(): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull().orEmpty()

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val installed = installedVersion()
        try {
            val release = parse(download(LATEST_RELEASE_URL))
            if (AppVersion.isNewer(release.version, installed)) {
                Result.Available(release, installed)
            } else {
                Result.UpToDate(installed)
            }
        } catch (t: Throwable) {
            Result.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun parse(json: String): Release {
        val root = JSONObject(json)
        val tag = root.getString("tag_name")
        val assets = root.optJSONArray("assets")
        val apk = assets?.let { list ->
            (0 until list.length())
                .map(list::getJSONObject)
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf(String::isNotBlank)
        }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            pageUrl = root.optString("html_url").ifBlank { RELEASES_PAGE_URL },
            apkUrl = apk,
            notes = root.optString("body").trim(),
        )
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "SkelloBadge (Android)")
        }
        try {
            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                // Aucune release publiée pour l'instant : rien à proposer, pas une panne.
                404 -> error("Aucune version publiée")
                // Limite de l'API anonyme (60 appels par heure et par adresse IP).
                403, 429 -> error("GitHub limite les vérifications, réessaie plus tard")
                else -> error("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPOSITORY = "alexisdechiara/SkelloBadge"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/$REPOSITORY/releases/latest"
        private const val TIMEOUT_MS = 15_000

        /**
         * Ouvre le téléchargement dans le navigateur. Android propose ensuite d'installer
         * l'APK par-dessus la version actuelle, sans perdre la configuration — à condition
         * qu'il soit signé de la même clé, ce que garantit la CI de publication.
         */
        fun downloadIntent(url: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
