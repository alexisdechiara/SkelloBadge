package fr.gaddiction.skellobadge.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.gaddiction.skellobadge.MainActivity
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.domain.SyncHealth

/**
 * Signale que le planning affiché n'est plus rafraîchi.
 *
 * Seule notification que l'application émette sans qu'un rappel soit dû. Elle est
 * discrète à dessein — canal d'importance basse, sans son ni vibration : elle ne demande
 * rien pour maintenant, elle constate que ce sur quoi reposent les rappels a vieilli.
 *
 * Elle se met à jour en place tant que la situation dure, et disparaît d'elle-même dès
 * qu'une récupération aboutit.
 */
object SyncNotification {

    /**
     * Pose, met à jour ou retire l'alerte selon l'ancienneté du planning.
     *
     * Appelée à chaque passage du rafraîchissement, y compris quand tout va bien : c'est
     * ce qui garantit qu'une alerte posée hier ne survit pas à la première réussite.
     */
    @SuppressLint("MissingPermission")
    fun update(context: Context, lastSuccessEpochMillis: Long, nowEpochMillis: Long) {
        val manager = NotificationManagerCompat.from(context)
        val age = SyncHealth.staleFor(lastSuccessEpochMillis, nowEpochMillis)
            ?: return manager.cancel(Notifications.NOTIFICATION_ID_SYNC_ERROR)

        if (!Notifications.canPost(context)) return
        manager.notify(
            Notifications.NOTIFICATION_ID_SYNC_ERROR,
            build(context, SyncHealth.describe(age)),
        )
    }

    private fun build(context: Context, age: String) =
        NotificationCompat.Builder(context, Notifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_badge)
            .setContentTitle(context.getString(R.string.notif_sync_error_title))
            .setContentText(context.getString(R.string.notif_sync_error_text, age))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_sync_error_text, age)),
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Elle se réactualise toutes les deux heures : sans cela, chaque passage
            // reviendrait sonner pour redire la même chose.
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            // L'écran d'accueil porte la cause exacte de l'échec ; c'est là qu'il faut aller.
            .setContentIntent(openApp(context))
            .build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        Notifications.NOTIFICATION_ID_SYNC_ERROR,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
