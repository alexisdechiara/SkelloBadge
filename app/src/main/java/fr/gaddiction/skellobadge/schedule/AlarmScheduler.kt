package fr.gaddiction.skellobadge.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.domain.Reminder
import fr.gaddiction.skellobadge.domain.ReminderKind
import java.time.ZonedDateTime

/**
 * Pose les alarmes exactes correspondant aux rappels à venir.
 *
 * Les alarmes ne survivent ni au redémarrage, ni à un changement d'heure, ni à une mise
 * à jour de l'application : elles sont donc reposées en bloc plutôt que mises à jour au
 * coup par coup. Les identifiants posés sont conservés pour pouvoir tout annuler
 * exactement, y compris après un redémarrage du processus.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()
    private val bookkeeping = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Annule tout ce qui était posé, puis repose l'intégralité des rappels fournis. */
    fun replaceAll(reminders: List<Reminder>, settings: AppSettings) {
        cancelAll()
        val posted = mutableSetOf<String>()
        reminders.forEach { reminder ->
            if (schedule(reminder, settings)) {
                posted += reminder.id.toString()
            }
        }
        bookkeeping.edit().putStringSet(KEY_SCHEDULED, posted).apply()
        Log.i(TAG, "Alarmes posees: " + posted.size)
    }

    /**
     * Reprogramme un rappel déjà émis, à partir de l'intention qui l'a déclenché.
     *
     * Sert à la fois pour la relance automatique (notification restée sans réponse) et
     * pour le report manuel. Le rappel reprogrammé est marqué comme relance et ne se
     * relancera donc pas à son tour : au plus un rappel supplémentaire par échéance.
     */
    fun scheduleDelayedFire(source: Intent, id: Int, delayMinutes: Int) {
        if (delayMinutes <= 0) return
        val intent = Intent(source).apply {
            setClass(context, ReminderReceiver::class.java)
            action = ReminderIntents.ACTION_FIRE
            data = ReminderIntents.uriFor(id, isNag = true)
            putExtra(ReminderIntents.EXTRA_IS_NAG, true)
            putExtra(ReminderIntents.EXTRA_NAG_MINUTES, 0)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(System.currentTimeMillis() + delayMinutes * MILLIS_PER_MINUTE, pending)
    }

    fun cancelNag(id: Int) = cancelOne(id, isNag = true)

    /**
     * Rappel de démonstration quelques secondes plus tard.
     *
     * Emprunte exactement le même chemin qu'un vrai rappel — alarme exacte, récepteur,
     * notification, ouverture de la badgeuse — pour que chacun puisse vérifier sur son
     * propre téléphone que la chaîne fonctionne. C'est le seul moyen de détecter à
     * l'avance une surcouche constructeur qui étoufferait les alarmes.
     */
    fun scheduleTest(settings: AppSettings) {
        val at = ZonedDateTime.now().plusSeconds(TEST_DELAY_SECONDS)
        val reminder = Reminder(
            at = at,
            actionAt = at,
            kind = ReminderKind.CLOCK_IN,
            title = "Test de notification",
            note = "Si tu vois ce rappel, les alarmes passent bien sur ce téléphone.",
        )
        setExact(
            at.toInstant().toEpochMilli(),
            // Pas de relance sur un test : une notification, et c'est tout.
            pendingIntent(reminder, settings.copy(nagAfterMinutes = 0), isNag = false),
        )
    }

    fun cancelAll() {
        bookkeeping.getStringSet(KEY_SCHEDULED, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .forEach { id ->
                cancelOne(id, isNag = false)
                cancelOne(id, isNag = true)
            }
        bookkeeping.edit().remove(KEY_SCHEDULED).apply()
    }

    private fun cancelOne(id: Int, isNag: Boolean) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderIntents.ACTION_FIRE
            data = ReminderIntents.uriFor(id, isNag)
        }
        PendingIntent.getBroadcast(context, id, intent, CANCEL_FLAGS)?.let { pending ->
            alarmManager?.cancel(pending)
            pending.cancel()
        }
    }

    private fun schedule(reminder: Reminder, settings: AppSettings): Boolean {
        val triggerAt = reminder.at.toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return false
        setExact(triggerAt, pendingIntent(reminder, settings, isNag = false))
        return true
    }

    private fun pendingIntent(
        reminder: Reminder,
        settings: AppSettings,
        isNag: Boolean,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id,
        ReminderIntents.fire(context, reminder, settings, isNag),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setExact(triggerAtMillis: Long, pending: PendingIntent) {
        val manager = alarmManager ?: return
        // USE_EXACT_ALARM rend normalement ce contrôle toujours vrai, mais on préfère
        // dégrader proprement plutôt que de laisser filer une SecurityException.
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarme exacte refusee, repli sur une alarme approximative", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val PREFS_NAME = "alarm_bookkeeping"
        const val KEY_SCHEDULED = "scheduled_ids"
        const val MILLIS_PER_MINUTE = 60_000L
        const val TEST_DELAY_SECONDS = 10L
        const val CANCEL_FLAGS = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    }
}
