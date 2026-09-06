package fr.gaddiction.skellobadge.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.gaddiction.skellobadge.notify.AlarmSignal
import fr.gaddiction.skellobadge.notify.FullScreenAlarmActivity
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

        // Le signal sonore appartient au processus, non à l'écran d'alarme : il retentit
        // même si le système refuse l'affichage plein écran.
        if (payload.shouldEscalate) {
            AlarmSignal.start(context)
        }

        ReminderNotification.post(context, payload, intent)
        Log.i(
            TAG,
            "Rappel " + payload.id + " tentative " + payload.attempt +
                (if (payload.shouldEscalate) " (plein ecran)" else ""),
        )

        // Une intention plein écran n'ouvre l'activité que si l'écran est éteint ou
        // verrouillé. Le test doit pouvoir montrer l'écran d'alarme sans cette condition :
        // l'application vient d'être au premier plan, le lancement est donc autorisé.
        if (payload.forceFullScreen) {
            runCatching {
                context.startActivity(FullScreenAlarmActivity.intent(context, intent))
            }.onFailure { Log.w(TAG, "Ouverture directe de l'alarme refusee", it) }
        }

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
