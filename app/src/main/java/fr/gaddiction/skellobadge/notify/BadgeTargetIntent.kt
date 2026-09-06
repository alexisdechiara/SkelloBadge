package fr.gaddiction.skellobadge.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.gaddiction.skellobadge.MainActivity
import fr.gaddiction.skellobadge.data.Contacts

/**
 * Construit l'intention qui ouvre la badgeuse.
 *
 * La cible ayant été résolue au moment de la planification, aucun détour par notre propre
 * application n'est nécessaire : le clic sur la notification ouvre directement Skello.
 */
object BadgeTargetIntent {

    fun resolve(context: Context, payload: ReminderPayload): Intent {
        val intent = forTarget(context, payload) ?: Intent(context, MainActivity::class.java)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun forTarget(context: Context, payload: ReminderPayload): Intent? {
        // Demande de confirmation : ce n'est pas la badgeuse qu'il faut ouvrir mais la
        // conversation avec le responsable.
        payload.contactNumber?.takeIf(String::isNotBlank)?.let { number ->
            return Contacts.messageIntent(number)
        }
        payload.targetPackage?.takeIf(String::isNotBlank)?.let { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { return it }
        }
        payload.targetUrl?.takeIf(String::isNotBlank)?.let { url ->
            return Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        return null
    }
}
