package fr.gaddiction.skellobadge.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.BadgeTarget
import fr.gaddiction.skellobadge.domain.Reminder

/**
 * Contrat des intentions échangées entre l'ordonnanceur, le récepteur d'alarme et les
 * actions de notification.
 *
 * La cible de badgeage est recopiée dans l'intention au moment de la planification
 * plutôt que relue à la volée : le récepteur peut ainsi construire directement
 * l'intention d'ouverture de la badgeuse, sans détour par notre propre application.
 */
object ReminderIntents {

    const val EXTRA_ID = "reminder_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_TITLE = "title"
    const val EXTRA_NOTE = "note"
    const val EXTRA_ACTION_AT = "action_at"
    const val EXTRA_TARGET_PACKAGE = "target_package"
    const val EXTRA_TARGET_URL = "target_url"
    const val EXTRA_NAG_MINUTES = "nag_minutes"
    const val EXTRA_IS_NAG = "is_nag"

    const val ACTION_FIRE = "fr.gaddiction.skellobadge.FIRE"
    const val ACTION_BADGED = "fr.gaddiction.skellobadge.BADGED"
    const val ACTION_SNOOZE = "fr.gaddiction.skellobadge.SNOOZE"

    private const val SCHEME = "skellobadge"

    /**
     * Une donnée d'URI distincte par alarme. Le rapprochement des PendingIntent ignore
     * les extras : sans cette URI, deux alarmes différentes seraient confondues.
     */
    fun uriFor(id: Int, isNag: Boolean): Uri {
        val suffix = if (isNag) "/nag" else ""
        return Uri.parse(SCHEME + "://reminder/" + id + suffix)
    }

    fun fire(
        context: Context,
        reminder: Reminder,
        settings: AppSettings,
        isNag: Boolean,
    ): Intent = Intent(context, ReminderReceiver::class.java).apply {
        action = ACTION_FIRE
        data = uriFor(reminder.id, isNag)
        putExtra(EXTRA_ID, reminder.id)
        putExtra(EXTRA_KIND, reminder.kind.name)
        putExtra(EXTRA_TITLE, reminder.title)
        putExtra(EXTRA_NOTE, reminder.note)
        putExtra(EXTRA_ACTION_AT, reminder.actionAt.toInstant().toEpochMilli())
        putExtra(EXTRA_NAG_MINUTES, if (isNag) 0 else settings.nagAfterMinutes)
        putExtra(EXTRA_IS_NAG, isNag)
        when (settings.targetKind) {
            BadgeTarget.APP -> putExtra(EXTRA_TARGET_PACKAGE, settings.targetPackage)
            BadgeTarget.URL -> putExtra(EXTRA_TARGET_URL, settings.targetUrl)
        }
    }
}
