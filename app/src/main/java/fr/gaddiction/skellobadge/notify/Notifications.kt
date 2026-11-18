package fr.gaddiction.skellobadge.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.Sound
import fr.gaddiction.skellobadge.domain.ReminderKind

/**
 * Un canal par type de rappel : chacun peut ainsi recevoir son propre son, sa propre
 * priorité et son propre réglage de contournement du mode Ne pas déranger, directement
 * depuis les paramètres système. C'est le comportement attendu d'une application de
 * rappel, et cela évite de réimplémenter ces réglages dans l'application.
 */
object Notifications {

    const val CHANNEL_CLOCK_IN = "clock_in"
    const val CHANNEL_BREAK = "break"
    const val CHANNEL_CLOCK_OUT = "clock_out"
    const val CHANNEL_SHIFT_CHANGE = "shift_change"
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_STANDBY = "standby_confirm"
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_UPDATES = "updates"

    const val NOTIFICATION_ID_SYNC_ERROR = 1
    const val NOTIFICATION_ID_UPDATE = 2

    /** Canal en vigueur pour ce type de rappel, avec le son actuellement choisi. */
    fun channelFor(context: Context, kind: ReminderKind): String =
        reminderChannelId(baseFor(kind), generation(context))

    private fun baseFor(kind: ReminderKind): String = when (kind) {
        ReminderKind.CLOCK_IN -> CHANNEL_CLOCK_IN
        ReminderKind.BREAK_OUT, ReminderKind.BREAK_IN -> CHANNEL_BREAK
        ReminderKind.SHIFT_CHANGE -> CHANNEL_SHIFT_CHANGE
        ReminderKind.CLOCK_OUT -> CHANNEL_CLOCK_OUT
        ReminderKind.STANDBY_CONFIRM -> CHANNEL_STANDBY
    }

    /**
     * Applique le son choisi dans les réglages aux canaux des rappels.
     *
     * Android fige le son d'un canal dès sa création : seule l'utilisatrice ou
     * l'utilisateur peut le modifier ensuite, depuis les paramètres système. Changer de son
     * revient donc à remplacer les canaux par une nouvelle génération — un même
     * identifiant supprimé puis recréé retrouverait ses anciens réglages, son compris.
     * L'importance et la vibration réglées dans Android sont reportées sur les nouveaux.
     */
    @Synchronized
    fun applyReminderSound(context: Context, sound: Sound) {
        val prefs = prefs(context)
        if (prefs.getString(KEY_SOUND, null) == sound.key && generation(context) > 0) return
        prefs.edit(commit = true) {
            putInt(KEY_GENERATION, generation(context) + 1)
            putString(KEY_SOUND, sound.key)
        }
        createChannels(context)
    }

    /**
     * POST_NOTIFICATIONS n'existe comme permission d'exécution qu'à partir d'Android 13.
     * En dessous, l'interroger renvoie « refusée » et bloquerait silencieusement toutes
     * les notifications sur Android 8 à 12, que cette application prend en charge.
     */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        // Premier lancement, ou mise à jour depuis une version sans choix de son : les
        // canaux d'origine portaient le son générique du téléphone, on passe au son par défaut.
        if (generation(context) == 0) {
            prefs(context).edit(commit = true) {
                putInt(KEY_GENERATION, 1)
                putString(KEY_SOUND, AppSettings.DEFAULT_REMINDER_SOUND.key)
            }
        }
        val generation = generation(context)
        val sound = Sound.parse(prefs(context).getString(KEY_SOUND, null))
            ?: AppSettings.DEFAULT_REMINDER_SOUND

        val reminders = REMINDER_CHANNELS.map { spec ->
            reminderChannel(context, manager, spec, generation, sound)
        }
        val current = reminders.map(NotificationChannel::getId).toSet()

        manager.createNotificationChannels(
            reminders + listOf(
                alarmChannel(context),
                channel(
                    context,
                    CHANNEL_STATUS,
                    R.string.channel_status_name,
                    R.string.channel_status_desc,
                    NotificationManager.IMPORTANCE_LOW,
                ),
                // Canal à part : on peut couper l'annonce des versions sans perdre
                // l'alerte de planning périmé, qui, elle, conditionne les rappels.
                channel(
                    context,
                    CHANNEL_UPDATES,
                    R.string.channel_updates_name,
                    R.string.channel_updates_desc,
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )

        // Les générations précédentes disparaîtraient d'elles-mêmes des notifications,
        // mais resteraient listées dans les paramètres Android, en double et sans effet.
        manager.notificationChannels
            .map(NotificationChannel::getId)
            .filter { id -> id !in current && REMINDER_CHANNELS.any { it.belongs(id) } }
            .forEach(manager::deleteNotificationChannel)
    }

    /**
     * Canal de l'escalade, volontairement muet et sans vibration.
     *
     * Le son et la vibration sont pilotés par [AlarmSignal], qui les joue en boucle
     * jusqu'à réponse. Les déclarer aussi sur le canal les faisait retentir deux fois,
     * avec un léger décalage : un écho à l'oreille, et une vibration doublée.
     */
    private fun alarmChannel(context: Context): NotificationChannel =
        NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            enableVibration(false)
            setShowBadge(true)
            setSound(null, null)
        }

    /**
     * Canal d'un type de rappel. S'il remplace une génération précédente, il en reprend
     * l'importance et la vibration : choisir un son ne doit pas défaire le reste des
     * réglages faits dans Android.
     */
    private fun reminderChannel(
        context: Context,
        manager: NotificationManager,
        spec: ReminderChannel,
        generation: Int,
        sound: Sound,
    ): NotificationChannel {
        val previous = manager.notificationChannels
            .filter { spec.belongs(it.id) }
            .maxByOrNull { generationOf(it.id) }
        return channel(
            context,
            reminderChannelId(spec.base, generation),
            spec.nameRes,
            spec.descriptionRes,
            previous?.importance ?: spec.importance,
        ).apply {
            setSound(Sounds.uriOf(context, sound, forAlarm = false), Sounds.attributes(false))
            previous?.let { enableVibration(it.shouldVibrate()) }
        }
    }

    private class ReminderChannel(
        val base: String,
        val nameRes: Int,
        val descriptionRes: Int,
        val importance: Int = NotificationManager.IMPORTANCE_HIGH,
    ) {
        /** Le canal d'origine, sans suffixe, ou l'une de ses générations. */
        fun belongs(id: String): Boolean = id == base || id.startsWith(base + SEPARATOR)
    }

    private val REMINDER_CHANNELS = listOf(
        ReminderChannel(
            CHANNEL_CLOCK_IN,
            R.string.channel_clock_in_name,
            R.string.channel_clock_in_desc,
        ),
        ReminderChannel(CHANNEL_BREAK, R.string.channel_break_name, R.string.channel_break_desc),
        ReminderChannel(
            CHANNEL_CLOCK_OUT,
            R.string.channel_clock_out_name,
            R.string.channel_clock_out_desc,
        ),
        ReminderChannel(
            CHANNEL_SHIFT_CHANGE,
            R.string.channel_shift_change_name,
            R.string.channel_shift_change_desc,
        ),
        // Importance par défaut : c'est une démarche à ne pas oublier, pas une
        // échéance qui doit interrompre ce qu'on est en train de faire.
        ReminderChannel(
            CHANNEL_STANDBY,
            R.string.channel_standby_name,
            R.string.channel_standby_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )

    private fun reminderChannelId(base: String, generation: Int): String =
        base + SEPARATOR + generation

    private fun generationOf(id: String): Int =
        id.substringAfterLast(SEPARATOR, "").toIntOrNull() ?: 0

    private fun generation(context: Context): Int = prefs(context).getInt(KEY_GENERATION, 0)

    /**
     * Tenu hors du DataStore des réglages : le récepteur d'alarme doit connaître le canal
     * en vigueur sur-le-champ, sans lecture asynchrone.
     */
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "notification_channels"
    private const val KEY_GENERATION = "reminder_generation"
    private const val KEY_SOUND = "reminder_sound"
    private const val SEPARATOR = "_v"

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        descriptionRes: Int,
        importance: Int = NotificationManager.IMPORTANCE_HIGH,
    ): NotificationChannel = NotificationChannel(id, context.getString(nameRes), importance).apply {
        description = context.getString(descriptionRes)
        enableVibration(importance >= NotificationManager.IMPORTANCE_DEFAULT)
        setShowBadge(true)
    }
}
