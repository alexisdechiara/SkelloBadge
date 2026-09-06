package fr.gaddiction.skellobadge.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import fr.gaddiction.skellobadge.schedule.AlarmScheduler
import fr.gaddiction.skellobadge.schedule.ReminderIntents
import fr.gaddiction.skellobadge.ui.rememberHaptics
import fr.gaddiction.skellobadge.ui.theme.SkelloBadgeTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Alarme plein écran, affichée par-dessus l'écran de verrouillage quand un badgeage est
 * resté ignoré trop longtemps.
 *
 * Une notification, même sonore, se balaie sans y penser. Cet écran demande une action
 * explicite : c'est le dernier filet avant l'oubli constaté par le directeur.
 */
class FullScreenAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val payload = ReminderPayload.from(intent)
        if (payload == null) {
            finish()
            return
        }

        // Le son est lancé par le récepteur, pas ici : cet écran ne fait que l'arrêter.
        // Ainsi l'alarme retentit même quand le système refuse l'affichage plein écran.

        setContent {
            SkelloBadgeTheme {
                AlarmScreen(
                    payload = payload,
                    onOpenBadge = {
                        dismiss(payload.id)
                        startActivity(BadgeTargetIntent.resolve(this, payload))
                        finish()
                    },
                    onDone = {
                        dismiss(payload.id)
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        AlarmSignal.stop()
        super.onDestroy()
    }

    private fun dismiss(id: Int) {
        AlarmSignal.stop()
        NotificationManagerCompat.from(this).cancel(id)
        AlarmScheduler(this).cancelChain(id)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    companion object {
        private const val SCHEME = "skellobadge"

        /** Reprend les extras du rappel pour que l'écran sache quoi afficher et quoi ouvrir. */
        fun intent(context: Context, source: Intent): Intent = Intent(source).apply {
            setClass(context, FullScreenAlarmActivity::class.java)
            action = Intent.ACTION_MAIN
            data = Uri.parse(
                SCHEME + "://alarm/" + source.getIntExtra(ReminderIntents.EXTRA_ID, 0),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)

@Composable
private fun AlarmScreen(
    payload: ReminderPayload,
    onOpenBadge: () -> Unit,
    onDone: () -> Unit,
) {
    val haptics = rememberHaptics()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // L'information tient dans le tiers haut de l'écran : l'heure d'abord, parce
            // que c'est elle qui dit s'il faut courir. Les actions restent en bas, sous
            // le pouce, à une place stable d'une alarme à l'autre.
            Spacer(Modifier.weight(0.6f))

            // Capitales évitées : en français elles crient, et se passent mal des accents.
            Text(
                "Badgeage en retard",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                TIME.format(payload.actionAt),
                style = MaterialTheme.typography.displayLarge,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                payload.titleText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                payload.shift,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            // « Sans réponse depuis 0 min » ne veut rien dire, et « ignoré » met en cause
            // l'utilisateur là où l'application ne fait que constater.
            if (payload.ignoredForMinutes >= 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sans réponse depuis " + payload.ignoredForMinutes + " min",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { haptics.confirm(); onOpenBadge() },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Ouvrir la badgeuse", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(4.dp))

            // Sans bordure : c'est la sortie discrète, pas une seconde proposition de
            // même poids que l'ouverture de la badgeuse.
            TextButton(
                onClick = { haptics.confirm(); onDone() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("J'ai déjà badgé", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
