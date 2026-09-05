package fr.gaddiction.skellobadge.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.gaddiction.skellobadge.data.PlanningRepository
import fr.gaddiction.skellobadge.data.SettingsRepository
import fr.gaddiction.skellobadge.domain.PlanningEngine
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Récupère le planning et repose les alarmes.
 *
 * Aucune contrainte réseau n'est imposée : sans connexion, le dépôt retombe sur le
 * dernier planning connu et il vaut mieux poser des alarmes légèrement datées que
 * n'en poser aucune.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(applicationContext)
        val settings = settingsRepository.current()
        if (!settings.isUsable) {
            Log.i(TAG, "Configuration incomplete, rien a planifier")
            return Result.success()
        }

        return try {
            val now = ZonedDateTime.now()
            val snapshot = PlanningRepository(applicationContext).load(settings, now)
            val reminders = PlanningEngine.upcomingReminders(snapshot.days, now, SCHEDULE_HORIZON)

            AlarmScheduler(applicationContext).replaceAll(reminders, settings)
            settingsRepository.update {
                it.copy(
                    lastSyncEpochMillis = System.currentTimeMillis(),
                    lastSyncError = snapshot.error.orEmpty(),
                )
            }
            Log.i(TAG, "Replanification terminee: " + reminders.size + " rappels")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Echec de la replanification", t)
            settingsRepository.update {
                it.copy(lastSyncError = t.message ?: t.javaClass.simpleName)
            }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RefreshWorker"
        private const val PERIODIC_NAME = "planning-refresh"
        private const val ONE_SHOT_NAME = "planning-refresh-now"

        /** Le flux Skello annonce un rafraîchissement horaire ; deux heures suffisent largement. */
        private const val PERIOD_HOURS = 2L

        /** Sept jours d'alarmes posées d'avance : de quoi absorber une longue coupure réseau. */
        private val SCHEDULE_HORIZON: Duration = Duration.ofDays(7)

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Replanification immédiate : démarrage, changement d'heure, retour dans l'application. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RefreshWorker>().build(),
            )
        }
    }
}
