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
            // Le badgeage est fait : on coupe la relance qui était armée.
            ReminderIntents.ACTION_BADGED -> scheduler.cancelNag(id)

            // Report : on rejoue le même rappel un peu plus tard.
            ReminderIntents.ACTION_SNOOZE -> {
                scheduler.cancelNag(id)
                scheduler.scheduleDelayedFire(intent, id, SNOOZE_MINUTES)
            }
        }
    }

    private companion object {
        const val SNOOZE_MINUTES = 5
    }
}
