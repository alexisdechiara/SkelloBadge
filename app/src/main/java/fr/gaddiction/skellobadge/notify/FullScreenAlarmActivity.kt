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

    private fun dismiss(id: Int) {
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
            Text(
                "BADGEAGE EN RETARD",
                style = MaterialTheme.typography.labelLarge,
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

            Spacer(Modifier.height(8.dp))

            Text(
                "Ignoré depuis " + payload.ignoredForMinutes + " min",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onOpenBadge,
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
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("J'ai déjà badgé", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
