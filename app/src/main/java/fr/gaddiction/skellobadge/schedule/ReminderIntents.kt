package fr.gaddiction.skellobadge.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.BadgeTarget
import fr.gaddiction.skellobadge.domain.Reminder
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Contrat des intentions échangées entre l'ordonnanceur, le récepteur d'alarme, l'alarme
 * plein écran et les actions de notification.
 *
 * Le texte affiché et la cible de badgeage sont recopiés dans l'intention au moment de la
 * planification plutôt que relus à la volée : le récepteur n'a besoin d'aucun accès aux
 * réglages, et peut construire directement l'intention d'ouverture de la badgeuse.
 */
object ReminderIntents {

    const val EXTRA_ID = "reminder_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_SHIFT = "shift"
    const val EXTRA_TITLE_TEXT = "title_text"
    const val EXTRA_BODY_TEXT = "body_text"
    const val EXTRA_NOTE = "note"
    const val EXTRA_ACTION_AT = "action_at"
    const val EXTRA_TARGET_PACKAGE = "target_package"
    const val EXTRA_TARGET_URL = "target_url"

    /** 0 pour le rappel initial, puis 1, 2, 3... pour chaque relance. */
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_NAG_INTERVAL = "nag_interval"
    const val EXTRA_NAG_MAX = "nag_max"
    const val EXTRA_FULLSCREEN_ENABLED = "fullscreen_enabled"
    const val EXTRA_FULLSCREEN_AFTER = "fullscreen_after"

    const val ACTION_FIRE = "fr.gaddiction.skellobadge.FIRE"
    const val ACTION_BADGED = "fr.gaddiction.skellobadge.BADGED"
    const val ACTION_SNOOZE = "fr.gaddiction.skellobadge.SNOOZE"

    private const val SCHEME = "skellobadge"
    private val TIME = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)

    /**
     * Une donnée d'URI distincte par alarme et par tentative. Le rapprochement des
     * PendingIntent ignore les extras : sans elle, une relance écraserait la précédente.
     */
    fun uriFor(id: Int, attempt: Int): Uri =
        Uri.parse(SCHEME + "://reminder/" + id + "/" + attempt)

    fun fire(context: Context, reminder: Reminder, settings: AppSettings): Intent {
        val wording = settings.wordingFor(reminder.kind)
        val time = TIME.format(reminder.actionAt)

        return Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            data = uriFor(reminder.id, attempt = 0)
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_KIND, reminder.kind.name)
            putExtra(EXTRA_SHIFT, reminder.title)
            putExtra(EXTRA_TITLE_TEXT, wording.title)
            putExtra(EXTRA_BODY_TEXT, wording.format(time, reminder.title))
            putExtra(EXTRA_NOTE, reminder.note)
            putExtra(EXTRA_ACTION_AT, reminder.actionAt.toInstant().toEpochMilli())
            putExtra(EXTRA_ATTEMPT, 0)
            putExtra(EXTRA_NAG_INTERVAL, settings.nagIntervalMinutes)
            putExtra(EXTRA_NAG_MAX, settings.nagMaxCount)
            putExtra(EXTRA_FULLSCREEN_ENABLED, settings.fullScreenAlarmEnabled)
            putExtra(EXTRA_FULLSCREEN_AFTER, settings.fullScreenAlarmAfterMinutes)
            when (settings.targetKind) {
                BadgeTarget.APP -> putExtra(EXTRA_TARGET_PACKAGE, settings.targetPackage)
                BadgeTarget.URL -> putExtra(EXTRA_TARGET_URL, settings.targetUrl)
            }
        }
    }
}
