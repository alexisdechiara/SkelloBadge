package fr.gaddiction.skellobadge.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.getSystemService
import fr.gaddiction.skellobadge.R
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

    const val NOTIFICATION_ID_SYNC_ERROR = 1

    fun channelFor(kind: ReminderKind): String = when (kind) {
        ReminderKind.CLOCK_IN -> CHANNEL_CLOCK_IN
        ReminderKind.BREAK_OUT, ReminderKind.BREAK_IN -> CHANNEL_BREAK
        ReminderKind.SHIFT_CHANGE -> CHANNEL_SHIFT_CHANGE
        ReminderKind.CLOCK_OUT -> CHANNEL_CLOCK_OUT
        ReminderKind.STANDBY_CONFIRM -> CHANNEL_STANDBY
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                channel(
                    context,
                    CHANNEL_CLOCK_IN,
                    R.string.channel_clock_in_name,
                    R.string.channel_clock_in_desc,
                ),
                channel(
                    context,
                    CHANNEL_BREAK,
                    R.string.channel_break_name,
                    R.string.channel_break_desc,
                ),
                channel(
                    context,
                    CHANNEL_CLOCK_OUT,
                    R.string.channel_clock_out_name,
                    R.string.channel_clock_out_desc,
                ),
                channel(
                    context,
                    CHANNEL_SHIFT_CHANGE,
                    R.string.channel_shift_change_name,
                    R.string.channel_shift_change_desc,
                ),
                // Importance par défaut : c'est une démarche à ne pas oublier, pas une
                // échéance qui doit interrompre ce qu'on est en train de faire.
                channel(
                    context,
                    CHANNEL_STANDBY,
                    R.string.channel_standby_name,
                    R.string.channel_standby_desc,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                alarmChannel(context),
                channel(
                    context,
                    CHANNEL_STATUS,
                    R.string.channel_status_name,
                    R.string.channel_status_desc,
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    /**
     * Canal de l'escalade. Il utilise le son et le flux audio des alarmes plutôt que ceux
     * des notifications : c'est ce qui le rend audible quand le téléphone est en sourdine,
     * et ce qui distingue un badgeage en retard d'une notification ordinaire.
     */
    private fun alarmChannel(context: Context): NotificationChannel =
        NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

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
