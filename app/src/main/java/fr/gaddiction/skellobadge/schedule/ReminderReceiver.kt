package fr.gaddiction.skellobadge.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.gaddiction.skellobadge.notify.ReminderNotification
import fr.gaddiction.skellobadge.notify.ReminderPayload

/**
 * Déclenché par AlarmManager à l'heure d'un rappel, puis à chaque relance.
 *
 * Chaque déclenchement programme lui-même le suivant : la chaîne ne s'arrête qu'au
 * badgeage confirmé, ou au plafond de relances. C'est ce qui permet de tenir un rythme
 * d'une relance par minute sans avoir à poser trente alarmes d'avance.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_FIRE) return

        val payload = ReminderPayload.from(intent) ?: run {
            Log.w(TAG, "Alarme recue sans charge utile exploitable")
            return
        }

        ReminderNotification.post(context, payload, intent)
        Log.i(
            TAG,
            "Rappel " + payload.id + " tentative " + payload.attempt +
                (if (payload.shouldEscalate) " (plein ecran)" else ""),
        )

        if (payload.hasFollowUp) {
            AlarmScheduler(context).scheduleFollowUp(
                source = intent,
                id = payload.id,
                nextAttempt = payload.attempt + 1,
                delayMinutes = payload.nagIntervalMinutes,
            )
        }
    }

    private companion object {
        const val TAG = "ReminderReceiver"
    }
}
