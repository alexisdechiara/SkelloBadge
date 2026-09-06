package fr.gaddiction.skellobadge.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    private val signal by lazy { AlarmSignal(this) }
    private val autoStop = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val payload = ReminderPayload.from(intent)
        if (payload == null) {
            finish()
            return
        }

        signal.start()
        // Filet de sécurité : si le téléphone reste hors de portée, on cesse de sonner
        // au bout de quelques minutes plutôt que de vider la batterie. L'écran, lui, reste.
        autoStop.postDelayed({ signal.stop() }, SOUND_TIMEOUT_MILLIS)

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
        autoStop.removeCallbacksAndMessages(null)
        signal.stop()
        super.onDestroy()
    }

    private fun dismiss(id: Int) {
        signal.stop()
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

        /** Au-delà, on arrête le son et la vibration ; l'écran d'alarme reste affiché. */
        private const val SOUND_TIMEOUT_MILLIS = 5 * 60 * 1000L

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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Capitales évitées : en français elles crient, et se passent mal des accents.
            Text(
                "Badgeage en retard",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                payload.titleText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                TIME.format(payload.actionAt),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Text(
                payload.shift,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            // « Ignoré depuis 0 min » ne veut rien dire, et « ignoré » met en cause
            // l'utilisateur là où l'application ne fait que constater.
            if (payload.ignoredForMinutes >= 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sans réponse depuis " + payload.ignoredForMinutes + " min",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Spacer(Modifier.height(48.dp))

            val haptics = rememberHaptics()

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

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { haptics.confirm(); onDone() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("J'ai déjà badgé", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
