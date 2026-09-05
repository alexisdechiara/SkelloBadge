package fr.gaddiction.skellobadge.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.gaddiction.skellobadge.MainActivity
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.schedule.ReminderIntents
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderNotification {

    private val TIME = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)

    fun post(context: Context, payload: ReminderPayload, source: Intent) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context)
            .notify(payload.id, build(context, payload, source))
    }

    /**
     * POST_NOTIFICATIONS n'existe comme permission d'exécution qu'à partir d'Android 13.
     * En dessous, l'interroger renvoie « refusée » et bloquerait silencieusement toutes
     * les notifications sur Android 8 à 12, que cette application prend en charge.
     */
    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun build(context: Context, payload: ReminderPayload, source: Intent) =
        NotificationCompat.Builder(context, Notifications.channelFor(payload.kind))
            .setSmallIcon(R.drawable.ic_stat_badge)
            .setContentTitle(titleFor(context, payload))
            .setContentText(textFor(context, payload))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextFor(context, payload)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(payload.actionAt.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openTarget(context, payload))
            .addAction(
                0,
                context.getString(R.string.action_badged),
                action(context, payload, source, ReminderIntents.ACTION_BADGED),
            )
            .addAction(
                0,
                context.getString(R.string.action_snooze),
                action(context, payload, source, ReminderIntents.ACTION_SNOOZE),
            )
            .build()

    private fun titleFor(context: Context, payload: ReminderPayload): String {
        val base = when (payload.kind) {
            ReminderKind.CLOCK_IN -> R.string.notif_clock_in_title
            ReminderKind.BREAK_OUT -> R.string.notif_break_out_title
            ReminderKind.BREAK_IN -> R.string.notif_break_in_title
            ReminderKind.SHIFT_CHANGE -> R.string.notif_shift_change_title
            ReminderKind.CLOCK_OUT -> R.string.notif_clock_out_title
        }.let(context::getString)

        return if (payload.isNag) {
            context.getString(R.string.notif_nag_prefix) + " " + base
        } else {
            base
        }
    }

    private fun textFor(context: Context, payload: ReminderPayload): String {
        val time = TIME.format(payload.actionAt)
        return when (payload.kind) {
            ReminderKind.CLOCK_IN ->
                context.getString(R.string.notif_clock_in_text, time, payload.title)

            ReminderKind.BREAK_OUT ->
                context.getString(R.string.notif_break_out_text, time)

            ReminderKind.BREAK_IN ->
                context.getString(R.string.notif_break_in_text, time, payload.title)

            ReminderKind.SHIFT_CHANGE ->
                context.getString(R.string.notif_shift_change_text, time, payload.title)

            ReminderKind.CLOCK_OUT ->
                context.getString(R.string.notif_clock_out_text, time)
        }
    }

    /**
     * La note du planning ne tient pas sur une ligne mais porte souvent l'information
     * la plus utile (heure de rendez-vous, personne à confirmer) : elle est réservée au
     * texte déplié.
     */
    private fun bigTextFor(context: Context, payload: ReminderPayload): String {
        val main = textFor(context, payload)
        val note = payload.note?.takeIf(String::isNotBlank) ?: return main
        return main + "\n\n" + note
    }

    /**
     * Ouvre directement la badgeuse. La cible ayant été résolue au moment de la
     * planification, aucun détour par notre propre application n'est nécessaire.
     */
    private fun openTarget(context: Context, payload: ReminderPayload): PendingIntent {
        val intent = targetIntent(context, payload)
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            payload.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun targetIntent(context: Context, payload: ReminderPayload): Intent? {
        payload.targetPackage?.takeIf(String::isNotBlank)?.let { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { return it }
        }
        payload.targetUrl?.takeIf(String::isNotBlank)?.let { url ->
            return Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        return null
    }

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
}
