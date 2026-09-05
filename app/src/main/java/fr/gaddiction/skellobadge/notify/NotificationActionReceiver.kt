package fr.gaddiction.skellobadge.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import fr.gaddiction.skellobadge.schedule.AlarmScheduler
import fr.gaddiction.skellobadge.schedule.ReminderIntents

/** Traite les deux boutons portés par une notification de rappel. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderIntents.EXTRA_ID, 0)
        if (id == 0) return

        NotificationManagerCompat.from(context).cancel(id)
        val scheduler = AlarmScheduler(context)

        when (intent.action) {
            // Le badgeage est fait : on coupe toute la chaîne de relances.
            ReminderIntents.ACTION_BADGED -> scheduler.cancelChain(id)

            // Report : on interrompt la chaîne en cours et on rejoue le rappel plus tard.
            ReminderIntents.ACTION_SNOOZE -> {
                val attempt = intent.getIntExtra(ReminderIntents.EXTRA_ATTEMPT, 0)
                scheduler.cancelChain(id)
                scheduler.scheduleFollowUp(intent, id, attempt + 1, SNOOZE_MINUTES)
            }
        }
    }

    private companion object {
        const val SNOOZE_MINUTES = 5
    }
}
