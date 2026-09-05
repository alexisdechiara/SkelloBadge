package fr.gaddiction.skellobadge.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Les alarmes exactes sont perdues au redémarrage, lors d'un changement d'heure ou de
 * fuseau, et à chaque mise à jour de l'application. Chacun de ces évènements déclenche
 * donc une replanification complète — c'est ce qui permet à l'utilisateur de ne jamais
 * avoir à rouvrir l'application.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                Log.i(TAG, "Replanification declenchee par " + intent.action)
                RefreshWorker.enqueuePeriodic(context)
                RefreshWorker.refreshNow(context)
            }
        }
    }

    private companion object {
        const val TAG = "SystemEventReceiver"
    }
}
