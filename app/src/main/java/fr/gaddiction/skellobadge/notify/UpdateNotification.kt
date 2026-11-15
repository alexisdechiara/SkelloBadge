package fr.gaddiction.skellobadge.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.gaddiction.skellobadge.MainActivity
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.data.SettingsRepository
import fr.gaddiction.skellobadge.data.UpdateChecker

/**
 * Annonce, sans insister, qu'une nouvelle version est publiée.
 *
 * Une fois par jour au plus : l'API anonyme de GitHub limite les appels, et une version
 * ne se publie pas d'heure en heure. Chaque version n'est annoncée qu'une fois — celle
 * qu'on a écartée d'un « Plus tard » ne l'est plus du tout. Canal d'importance basse,
 * sans son : une mise à jour peut attendre la fin du service.
 */
object UpdateNotification {

    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /**
     * Ne lève jamais : un échec de vérification ne doit en aucun cas compromettre la
     * replanification des rappels qui l'appelle.
     */
    suspend fun checkIfDue(context: Context, repository: SettingsRepository) {
        runCatching {
            val settings = repository.current()
            val now = System.currentTimeMillis()
            if (!settings.updateCheckEnabled) return
            if (now - settings.lastUpdateCheckEpochMillis < CHECK_INTERVAL_MS) return

            when (val result = UpdateChecker(context).check()) {
                is UpdateChecker.Result.Available -> {
                    val version = result.release.version
                    repository.update {
                        it.copy(lastUpdateCheckEpochMillis = now, notifiedUpdateVersion = version)
                    }
                    if (version != settings.notifiedUpdateVersion &&
                        version != settings.dismissedUpdateVersion
                    ) {
                        post(context, result)
                    }
                }

                is UpdateChecker.Result.UpToDate -> {
                    repository.update { it.copy(lastUpdateCheckEpochMillis = now) }
                    cancel(context)
                }

                // Réseau absent, limite atteinte : on retentera au prochain passage.
                is UpdateChecker.Result.Failed -> Unit
            }
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(Notifications.NOTIFICATION_ID_UPDATE)
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, result: UpdateChecker.Result.Available) {
        if (!Notifications.canPost(context)) return
        val text = context.getString(
            R.string.notif_update_text,
            result.installed.ifBlank { "version actuelle" },
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_badge)
            .setContentTitle(context.getString(R.string.notif_update_title, result.release.version))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            // L'accueil porte la bannière : un geste de plus y installe la version, avec
            // la progression du téléchargement et les nouveautés sous les yeux.
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context)
            .notify(Notifications.NOTIFICATION_ID_UPDATE, notification)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            Notifications.NOTIFICATION_ID_UPDATE,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
