package fr.gaddiction.skellobadge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.gaddiction.skellobadge.data.UpdateChecker
import fr.gaddiction.skellobadge.data.UpdateInstaller
import fr.gaddiction.skellobadge.domain.ReleaseNotes

/**
 * Avancement et actions d'une mise à jour, partagés par la bannière de l'accueil et la
 * section des réglages : les deux disent la même chose dans les mêmes mots.
 *
 * Le bouton principal change de rôle avec l'état plutôt que d'en côtoyer d'autres :
 * « Mettre à jour », puis « Autoriser l'installation » si Android l'exige, puis rien de
 * cliquable pendant le téléchargement. Un échec propose toujours le navigateur en repli,
 * pour qu'aucune panne de l'installation intégrée ne laisse sans issue.
 */
@Composable
fun UpdateInstallPanel(
    release: UpdateChecker.Release,
    state: UpdateInstaller.State,
    textColor: Color,
    haptics: Haptics,
    onInstall: () -> Unit,
    onAllow: () -> Unit,
    onBrowser: () -> Unit,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    extraActions: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is UpdateInstaller.State.NeedsPermission -> StatusText(
                "Android demande d'autoriser cette application à installer ses mises à " +
                    "jour, une seule fois. Reviens ensuite ici : le téléchargement démarrera " +
                    "de lui-même.",
                textColor,
            )

            is UpdateInstaller.State.Downloading -> {
                StatusText(
                    "Téléchargement" + (state.progress?.let { " · " + (it * 100).toInt() + " %" } ?: "…"),
                    textColor,
                )
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }

            UpdateInstaller.State.Installing -> StatusText(
                "Confirme l'installation dans la fenêtre d'Android. La configuration est " +
                    "conservée.",
                textColor,
            )

            is UpdateInstaller.State.Failed -> StatusText(
                state.reason + ".",
                MaterialTheme.colorScheme.error,
            )

            UpdateInstaller.State.Idle -> Unit
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val busy = state is UpdateInstaller.State.Downloading ||
                state == UpdateInstaller.State.Installing
            when {
                // Sans APK joint à la release, l'installation intégrée n'a rien à installer.
                release.apkUrl == null -> Button(
                    onClick = { haptics.confirm(); onBrowser() },
                    colors = buttonColors,
                ) { Text("Mettre à jour") }

                state is UpdateInstaller.State.NeedsPermission -> Button(
                    onClick = { haptics.confirm(); onAllow() },
                    colors = buttonColors,
                ) { Text("Autoriser l'installation") }

                else -> Button(
                    onClick = { haptics.confirm(); onInstall() },
                    enabled = !busy,
                    colors = buttonColors,
                ) {
                    Text(
                        when {
                            busy -> "Mise à jour en cours"
                            state is UpdateInstaller.State.Failed -> "Réessayer"
                            else -> "Mettre à jour"
                        },
                    )
                }
            }
            if (state is UpdateInstaller.State.Failed && release.apkUrl != null) {
                TextButton(onClick = { haptics.click(); onBrowser() }) {
                    Text("Par le navigateur", color = textColor)
                }
            }
            extraActions()
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Notes de version en texte simple, une puce par nouveauté. */
@Composable
fun ReleaseNotesList(notes: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        ReleaseNotes.lines(notes).forEach { line ->
            Row(modifier = Modifier.padding(top = 3.dp)) {
                if (line.bullet) {
                    Text("•  ", style = MaterialTheme.typography.bodySmall, color = color)
                }
                Text(
                    line.text,
                    style = if (line.bullet) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = color,
                )
            }
        }
    }
}
