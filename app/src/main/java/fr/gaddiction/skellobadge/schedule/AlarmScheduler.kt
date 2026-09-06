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
 * Pose les alarmes exactes correspondant aux rappels à venir, ainsi que la chaîne de
 * relances qui suit chaque rappel non confirmé.
 *
 * Les alarmes ne survivent ni au redémarrage, ni à un changement d'heure, ni à une mise
 * à jour de l'application : elles sont donc reposées en bloc plutôt que mises à jour au
 * coup par coup. Chaque alarme posée est inscrite dans un registre local afin de pouvoir
 * être annulée exactement, y compris après un redémarrage du processus.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()
    private val registry = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Annule tout ce qui était posé, puis repose l'intégralité des rappels fournis. */
    @Synchronized
    fun replaceAll(reminders: List<Reminder>, settings: AppSettings) {
        cancelAll()
        val posted = mutableSetOf<String>()
        reminders.forEach { reminder ->
            val triggerAt = reminder.at.toInstant().toEpochMilli()
            if (triggerAt > System.currentTimeMillis()) {
                setExact(triggerAt, pendingFor(ReminderIntents.fire(context, reminder, settings), reminder.id))
                posted += entry(reminder.id, attempt = 0)
            }
        }
        registry.edit().putStringSet(KEY_PENDING, posted).apply()
        Log.i(TAG, "Alarmes posees: " + posted.size)
    }

    /**
     * Programme la relance suivante à partir de l'intention qui vient de se déclencher.
     * Le compteur de tentative est porté par l'intention elle-même, ce qui évite d'avoir
     * à tenir un état partagé entre le récepteur et l'ordonnanceur.
     */
    @Synchronized
    fun scheduleFollowUp(source: Intent, id: Int, nextAttempt: Int, delayMinutes: Int) {
        if (delayMinutes <= 0) return
        val intent = Intent(source).apply {
            setClass(context, ReminderReceiver::class.java)
            action = ReminderIntents.ACTION_FIRE
            data = ReminderIntents.uriFor(id, nextAttempt)
            putExtra(ReminderIntents.EXTRA_ATTEMPT, nextAttempt)
        }
        setExact(
            System.currentTimeMillis() + delayMinutes * MILLIS_PER_MINUTE,
            pendingFor(intent, id),
        )
        registry.edit()
            .putStringSet(KEY_PENDING, pendingEntries() + entry(id, nextAttempt))
            .apply()
    }

    /**
     * Coupe toute la chaîne de relances d'un rappel. Appelé dès que le badgeage est
     * confirmé : c'est ce qui fait taire l'application pour de bon.
     */
    @Synchronized
    fun cancelChain(id: Int) {
        val remaining = pendingEntries().filterNot { it.startsWith("$id:") }.toSet()
        (0..MAX_ATTEMPT_SWEEP).forEach { attempt -> cancelOne(id, attempt) }
        registry.edit().putStringSet(KEY_PENDING, remaining).apply()
    }

    @Synchronized
    fun cancelAll() {
        pendingEntries().forEach { raw ->
            val parts = raw.split(':')
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val attempt = parts.getOrNull(1)?.toIntOrNull() ?: 0
            cancelOne(id, attempt)
        }
        registry.edit().remove(KEY_PENDING).apply()
    }

    /**
     * Rappel de démonstration quelques secondes plus tard.
     *
     * Emprunte exactement le même chemin qu'un vrai rappel — alarme exacte, récepteur,
     * notification, ouverture de la badgeuse — pour que chacun puisse vérifier sur son
     * propre téléphone que la chaîne fonctionne. C'est le seul moyen de détecter à
     * l'avance une surcouche constructeur qui étoufferait les alarmes.
     */
    fun scheduleTest(settings: AppSettings) {
        // Pas de relance ni d'escalade sur un test simple : une notification, et c'est tout.
        scheduleProbe(
            settings.copy(nagIntervalMinutes = 0, fullScreenAlarmEnabled = false),
            title = "Test de notification",
            note = "Si tu vois ce rappel, les alarmes passent bien sur ce téléphone.",
        )
    }

    /**
     * Rappel de démonstration quelques secondes plus tard.
     *
     * Emprunte exactement le même chemin qu'un vrai rappel — alarme exacte, récepteur,
     * notification, ouverture de la badgeuse — pour que chacun puisse vérifier sur son
     * propre téléphone que la chaîne fonctionne. C'est le seul moyen de détecter à
     * l'avance une surcouche constructeur qui étoufferait les alarmes.
     */

    /**
     * Déclenche immédiatement la version escaladée du rappel.
     *
     * C'est le mécanisme le plus susceptible d'être bloqué — permission d'affichage plein
     * écran refusée sur Android 14+, ou surcouche constructeur — et donc celui qu'il faut
     * pouvoir éprouver sur chaque téléphone avant d'en dépendre.
     */
    fun scheduleFullScreenTest(settings: AppSettings) {
        scheduleProbe(
            settings.copy(
                nagIntervalMinutes = 1,
                nagMaxCount = 0,
                fullScreenAlarmEnabled = true,
                fullScreenAlarmAfterMinutes = 0,
            ),
            title = "Test de l'alarme plein écran",
            note = "Si cet écran ne s'est pas ouvert, la permission plein écran est refusée.",
            forceFullScreen = true,
        )
    }

    private fun scheduleProbe(
        settings: AppSettings,
        title: String,
        note: String,
        forceFullScreen: Boolean = false,
    ) {
        val at = ZonedDateTime.now().plusSeconds(TEST_DELAY_SECONDS)
        val reminder = Reminder(
            at = at,
            actionAt = at,
            kind = ReminderKind.CLOCK_IN,
            title = title,
            note = note,
        )
        setExact(
            at.toInstant().toEpochMilli(),
            pendingFor(
                ReminderIntents.fire(context, reminder, settings, forceFullScreen),
                reminder.id,
            ),
        )
    }

    private fun pendingEntries(): Set<String> =
        registry.getStringSet(KEY_PENDING, emptySet()).orEmpty()

    private fun entry(id: Int, attempt: Int) = "$id:$attempt"

    private fun cancelOne(id: Int, attempt: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderIntents.ACTION_FIRE
            data = ReminderIntents.uriFor(id, attempt)
        }
        PendingIntent.getBroadcast(context, id, intent, CANCEL_FLAGS)?.let { pending ->
            alarmManager?.cancel(pending)
            pending.cancel()
        }
    }

    private fun pendingFor(intent: Intent, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            intent,
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
        const val KEY_PENDING = "pending_alarms"
        const val MILLIS_PER_MINUTE = 60_000L
        const val TEST_DELAY_SECONDS = 1L
        const val CANCEL_FLAGS = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE

        /**
         * Balayage de sécurité à l'annulation : le registre suffit en temps normal, mais
         * une relance posée juste avant l'arrêt du processus pourrait ne pas y figurer.
         */
        const val MAX_ATTEMPT_SWEEP = 60
    }
}
