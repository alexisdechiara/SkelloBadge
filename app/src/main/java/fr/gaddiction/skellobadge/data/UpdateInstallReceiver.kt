package fr.gaddiction.skellobadge.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Reçoit l'issue d'une session d'installation ouverte par [UpdateInstaller].
 *
 * Non exporté : seul le PendingIntent de l'application elle-même peut l'atteindre, si
 * bien que l'intention de confirmation qu'il relaie vient nécessairement du système.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (status) {
            // Android demande à l'utilisateur de confirmer : on affiche sa fenêtre.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmationIntent(intent)
                if (confirm == null) {
                    UpdateInstaller.report(UpdateInstaller.State.Failed("Android n'a pas ouvert sa fenêtre d'installation"))
                    return
                }
                runCatching {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    UpdateInstaller.report(UpdateInstaller.State.Failed("Impossible d'ouvrir la fenêtre d'installation"))
                }
            }

            // Le processus est remplacé dans la foulée : rien de plus à faire ici.
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.report(UpdateInstaller.State.Idle)

            // « Annuler » dans la fenêtre du système : un choix, pas une panne.
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstaller.report(UpdateInstaller.State.Idle)

            else -> UpdateInstaller.report(
                UpdateInstaller.State.Failed(
                    explain(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
                ),
            )
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    /** Ce qu'il faut faire, plutôt qu'un code d'erreur du gestionnaire de paquets. */
    private fun explain(status: Int, message: String?): String = when (status) {
        // Cas typique : une version compilée sur un poste de développement, signée d'une
        // autre clé que celle des releases.
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "Cette version n'est pas signée comme celle installée. Désinstalle l'application " +
                "puis installe la nouvelle depuis la page de la version"

        PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation bloquée par le téléphone"
        PackageInstaller.STATUS_FAILURE_INVALID -> "Le fichier téléchargé n'est pas un APK valide"
        else -> message?.takeIf(String::isNotBlank) ?: "Installation impossible"
    }

    companion object {
        const val ACTION_STATUS = "fr.gaddiction.skellobadge.action.UPDATE_INSTALL_STATUS"
    }
}
