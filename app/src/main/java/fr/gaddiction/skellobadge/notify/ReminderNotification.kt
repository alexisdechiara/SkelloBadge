package fr.gaddiction.skellobadge.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.schedule.ReminderIntents

object ReminderNotification {

    // La permission est vérifiée par Notifications.canPost, que lint ne sait pas suivre.
    @SuppressLint("MissingPermission")
    fun post(context: Context, payload: ReminderPayload, source: Intent) {
        if (!Notifications.canPost(context)) return
        NotificationManagerCompat.from(context)
            .notify(payload.id, build(context, payload, source))
    }

    private fun build(context: Context, payload: ReminderPayload, source: Intent) =
        NotificationCompat.Builder(context, channelFor(payload))
            .setSmallIcon(R.drawable.ic_stat_badge)
            .setContentTitle(titleFor(context, payload))
            .setContentText(payload.bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextFor(payload)))
            .setCategory(
                if (payload.shouldEscalate) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_REMINDER
                },
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setWhen(payload.actionAt.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(
                activity(context, payload.id, BadgeTargetIntent.resolve(context, payload)),
            )
            .apply {
                // « J'ai badgé » n'a aucun sens sur une demande de confirmation : il n'y a
                // rien à badger, et le simple fait de toucher la notification ouvre déjà
                // la conversation avec le responsable.
                if (payload.kind != ReminderKind.STANDBY_CONFIRM) {
                    addAction(
                        0,
                        context.getString(R.string.action_badged),
                        action(context, payload, source, ReminderIntents.ACTION_BADGED),
                    )
                    addAction(
                        0,
                        context.getString(R.string.action_snooze),
                        action(context, payload, source, ReminderIntents.ACTION_SNOOZE),
                    )
                }

                if (payload.shouldEscalate) {
                    // L'intention plein écran ouvre l'alarme par-dessus l'écran de
                    // verrouillage. Si le système la refuse — permission non accordée sur
                    // Android 14+ — la notification reste affichée en tête de liste.
                    setFullScreenIntent(
                        activity(
                            context,
                            payload.id + FULLSCREEN_REQUEST_OFFSET,
                            FullScreenAlarmActivity.intent(context, source),
                        ),
                        true,
                    )
                    setOngoing(true)
                }
            }
            .build()

    private fun channelFor(payload: ReminderPayload): String =
        if (payload.shouldEscalate) Notifications.CHANNEL_ALARM else Notifications.channelFor(payload.kind)

    private fun titleFor(context: Context, payload: ReminderPayload): String = when {
        payload.shouldEscalate -> context.getString(
            R.string.notif_late_prefix,
            payload.ignoredForMinutes,
        ) + " " + payload.titleText

        payload.attempt > 0 -> context.getString(R.string.notif_nag_prefix) + " " + payload.titleText

        else -> payload.titleText
    }

    /**
     * La note du planning ne tient pas sur une ligne mais porte souvent l'information la
     * plus utile (heure de rendez-vous, personne à confirmer) : elle est réservée au
     * texte déplié.
     */
    private fun bigTextFor(payload: ReminderPayload): String {
        val note = payload.note?.takeIf(String::isNotBlank) ?: return payload.bodyText
        return payload.bodyText + "\n\n" + note
    }

    private fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(
        context: Context,
        payload: ReminderPayload,
        source: Intent,
        actionName: String,
    ): PendingIntent {
        // On recopie les extras d'origine : le report doit pouvoir reconstruire
        // exactement le même rappel quelques minutes plus tard.
        val intent = Intent(source).apply {
            setClass(context, NotificationActionReceiver::class.java)
            action = actionName
            data = Uri.parse("skellobadge://action/" + actionName + "/" + payload.id)
        }
        return PendingIntent.getBroadcast(
            context,
            payload.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Décalage pour ne pas confondre le PendingIntent plein écran avec celui du clic. */
    private const val FULLSCREEN_REQUEST_OFFSET = 1_000_000
}
