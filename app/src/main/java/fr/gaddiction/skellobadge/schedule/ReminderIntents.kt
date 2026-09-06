package fr.gaddiction.skellobadge.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.BadgeTarget
import fr.gaddiction.skellobadge.domain.Reminder
import fr.gaddiction.skellobadge.domain.ReminderKind
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

    /** Numéro du responsable : le rappel de confirmation ouvre la messagerie dessus. */
    const val EXTRA_CONTACT_NUMBER = "contact_number"

    /** 0 pour le rappel initial, puis 1, 2, 3... pour chaque relance. */
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_NAG_INTERVAL = "nag_interval"
    const val EXTRA_NAG_MAX = "nag_max"
    const val EXTRA_FULLSCREEN_ENABLED = "fullscreen_enabled"
    const val EXTRA_FULLSCREEN_AFTER = "fullscreen_after"

    /**
     * Ouvre l'écran d'alarme sans attendre que le téléphone soit verrouillé.
     *
     * Réservé au bouton de test : une intention plein écran n'ouvre l'activité que si
     * l'écran est éteint ou verrouillé, sinon le système se contente d'une bannière. Pour
     * éprouver l'écran d'alarme, il faut donc pouvoir le demander explicitement.
     */
    const val EXTRA_FORCE_FULLSCREEN = "force_fullscreen"

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

    fun fire(
        context: Context,
        reminder: Reminder,
        settings: AppSettings,
        forceFullScreen: Boolean = false,
    ): Intent {
        val wording = settings.wordingFor(reminder.kind)
        val time = TIME.format(reminder.actionAt)

        // La demande de confirmation d'une réserve est une démarche, pas un badgeage :
        // ni relance à la minute, ni alarme plein écran, et rien à ouvrir dans la badgeuse.
        val isErrand = reminder.kind == ReminderKind.STANDBY_CONFIRM

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
            putExtra(EXTRA_NAG_INTERVAL, if (isErrand) 0 else settings.nagIntervalMinutes)
            putExtra(EXTRA_NAG_MAX, settings.nagMaxCount)
            putExtra(
                EXTRA_FULLSCREEN_ENABLED,
                !isErrand && settings.fullScreenAlarmEnabled,
            )
            putExtra(EXTRA_FULLSCREEN_AFTER, settings.fullScreenAlarmAfterMinutes)
            putExtra(EXTRA_FORCE_FULLSCREEN, forceFullScreen)
            if (isErrand) {
                putExtra(EXTRA_CONTACT_NUMBER, settings.contactNumber)
            } else {
                when (settings.targetKind) {
                    BadgeTarget.APP -> putExtra(EXTRA_TARGET_PACKAGE, settings.targetPackage)
                    BadgeTarget.URL -> putExtra(EXTRA_TARGET_URL, settings.targetUrl)
                }
            }
        }
    }
}
