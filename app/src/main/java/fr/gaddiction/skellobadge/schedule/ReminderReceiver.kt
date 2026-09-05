package fr.gaddiction.skellobadge.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.gaddiction.skellobadge.notify.ReminderNotification
import fr.gaddiction.skellobadge.notify.ReminderPayload

/** Déclenché par AlarmManager à l'heure d'un rappel. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_FIRE) return

        val payload = ReminderPayload.from(intent) ?: run {
            Log.w(TAG, "Alarme recue sans charge utile exploitable")
            return
        }

        ReminderNotification.post(context, payload, intent)

        // Une seule relance, et seulement si la notification d'origine n'en était pas une.
        if (!payload.isNag && payload.nagMinutes > 0) {
            AlarmScheduler(context).scheduleDelayedFire(intent, payload.id, payload.nagMinutes)
        }
    }

    private companion object {
        const val TAG = "ReminderReceiver"
    }
}
