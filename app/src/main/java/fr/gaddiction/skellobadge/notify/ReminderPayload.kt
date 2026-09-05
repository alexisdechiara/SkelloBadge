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
    val title: String,
    val note: String?,
    val actionAt: ZonedDateTime,
    val targetPackage: String?,
    val targetUrl: String?,
    val nagMinutes: Int,
    val isNag: Boolean,
) {
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
                title = intent.getStringExtra(ReminderIntents.EXTRA_TITLE).orEmpty(),
                note = intent.getStringExtra(ReminderIntents.EXTRA_NOTE),
                actionAt = Instant.ofEpochMilli(actionAtMillis).atZone(zone),
                targetPackage = intent.getStringExtra(ReminderIntents.EXTRA_TARGET_PACKAGE),
                targetUrl = intent.getStringExtra(ReminderIntents.EXTRA_TARGET_URL),
                nagMinutes = intent.getIntExtra(ReminderIntents.EXTRA_NAG_MINUTES, 0),
                isNag = intent.getBooleanExtra(ReminderIntents.EXTRA_IS_NAG, false),
            )
        }
    }
}
