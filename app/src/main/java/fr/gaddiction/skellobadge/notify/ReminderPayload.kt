package fr.gaddiction.skellobadge.notify

import android.content.Intent
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.schedule.ReminderIntents
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Ce qu'une alarme transporte jusqu'au moment de l'affichage. */
data class ReminderPayload(
    val id: Int,
    val kind: ReminderKind,
    val shift: String,
    val titleText: String,
    val bodyText: String,
    val note: String?,
    val actionAt: ZonedDateTime,
    val targetPackage: String?,
    val targetUrl: String?,
    /** 0 pour le rappel initial, puis 1, 2, 3... pour chaque relance. */
    val attempt: Int,
    val nagIntervalMinutes: Int,
    val nagMaxCount: Int,
    val fullScreenEnabled: Boolean,
    val fullScreenAfterMinutes: Int,
    /** Test : ouvrir l'écran d'alarme immédiatement, sans attendre un écran verrouillé. */
    val forceFullScreen: Boolean = false,
) {
    /** Depuis combien de minutes le rappel est resté sans réponse. */
    val ignoredForMinutes: Int get() = attempt * nagIntervalMinutes

    /** Au-delà du seuil, on passe de la notification discrète à l'alarme plein écran. */
    val shouldEscalate: Boolean
        get() = fullScreenEnabled &&
            nagIntervalMinutes > 0 &&
            ignoredForMinutes >= fullScreenAfterMinutes

    val hasFollowUp: Boolean
        get() = nagIntervalMinutes > 0 && attempt < nagMaxCount

    companion object {
        fun from(intent: Intent, zone: ZoneId = ZoneId.systemDefault()): ReminderPayload? {
            val id = intent.getIntExtra(ReminderIntents.EXTRA_ID, 0)
            if (id == 0) return null
            val kind = intent.getStringExtra(ReminderIntents.EXTRA_KIND)
                ?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
                ?: return null
            val actionAtMillis = intent.getLongExtra(ReminderIntents.EXTRA_ACTION_AT, 0L)
            if (actionAtMillis == 0L) return null

            return ReminderPayload(
                id = id,
                kind = kind,
                shift = intent.getStringExtra(ReminderIntents.EXTRA_SHIFT).orEmpty(),
                titleText = intent.getStringExtra(ReminderIntents.EXTRA_TITLE_TEXT).orEmpty(),
                bodyText = intent.getStringExtra(ReminderIntents.EXTRA_BODY_TEXT).orEmpty(),
                note = intent.getStringExtra(ReminderIntents.EXTRA_NOTE),
                actionAt = Instant.ofEpochMilli(actionAtMillis).atZone(zone),
                targetPackage = intent.getStringExtra(ReminderIntents.EXTRA_TARGET_PACKAGE),
                targetUrl = intent.getStringExtra(ReminderIntents.EXTRA_TARGET_URL),
                attempt = intent.getIntExtra(ReminderIntents.EXTRA_ATTEMPT, 0),
                nagIntervalMinutes = intent.getIntExtra(ReminderIntents.EXTRA_NAG_INTERVAL, 0),
                nagMaxCount = intent.getIntExtra(ReminderIntents.EXTRA_NAG_MAX, 0),
                fullScreenEnabled =
                    intent.getBooleanExtra(ReminderIntents.EXTRA_FULLSCREEN_ENABLED, false),
                fullScreenAfterMinutes =
                    intent.getIntExtra(ReminderIntents.EXTRA_FULLSCREEN_AFTER, 0),
                forceFullScreen =
                    intent.getBooleanExtra(ReminderIntents.EXTRA_FORCE_FULLSCREEN, false),
            )
        }
    }
}
