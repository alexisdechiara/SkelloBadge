package fr.gaddiction.skellobadge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP = DateTimeFormatter.ofPattern("d MMMM 'à' HH'h'mm", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MinutesSetting(
                title = "Avance sur la prise de poste",
                value = settings.clockInLeadMinutes,
                onChange = { viewModel.update { s -> s.copy(clockInLeadMinutes = it) } },
            )

            MinutesSetting(
                title = "Avance sur le retour de coupure",
                value = settings.breakInLeadMinutes,
                onChange = { viewModel.update { s -> s.copy(breakInLeadMinutes = it) } },
            )

            MinutesSetting(
                title = "Relance si tu ne réponds pas",
                subtitle = "0 pour désactiver la relance",
                value = settings.nagAfterMinutes,
                onChange = { viewModel.update { s -> s.copy(nagAfterMinutes = it) } },
            )

            MinutesSetting(
                title = "Écart maximal pour un enchaînement",
                subtitle = "En dessous, deux services collés donnent un seul rappel de bascule",
                value = settings.shiftChangeMaxGapMinutes,
                onChange = { viewModel.update { s -> s.copy(shiftChangeMaxGapMinutes = it) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ListItem(
                headlineContent = { Text("Coupure de midi forcée") },
                supportingContent = {
                    Text(
                        "Ajoute un rappel à 12h et 13h sur les journées longues dont le " +
                            "planning ne prévoit aucune coupure. La plupart de tes journées " +
                            "étant d'un seul tenant, ce réglage crée beaucoup de rappels.",
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.lunchFallbackEnabled,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(lunchFallbackEnabled = checked) }
                        },
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Source", style = MaterialTheme.typography.titleSmall)
            Text(
                when (settings.source) {
                    fr.gaddiction.skellobadge.data.PlanningSource.ICS -> settings.icsUrl
                    fr.gaddiction.skellobadge.data.PlanningSource.DEVICE_CALENDAR ->
                        settings.calendarIds.size.toString() + " calendrier(s)"
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Badgeuse", style = MaterialTheme.typography.titleSmall)
            Text(
                settings.targetLabel.ifBlank { settings.targetUrl }.ifBlank { "Non définie" },
                style = MaterialTheme.typography.bodySmall,
            )

            if (settings.lastSyncEpochMillis > 0) {
                Text(
                    "Dernière synchronisation : " + STAMP.format(
                        Instant.ofEpochMilli(settings.lastSyncEpochMillis)
                            .atZone(ZoneId.systemDefault()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                onClick = { viewModel.sendTestReminder() },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Envoyer un rappel de test dans 10 s") }

            Text(
                "Emprunte le même chemin qu'un vrai rappel. Si rien n'arrive, c'est que " +
                    "la surcouche du téléphone bloque les alarmes en veille.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                onClick = { viewModel.update { it.copy(configured = false) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Reprendre la configuration") }
        }
    }
}

@Composable
private fun MinutesSetting(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    subtitle: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            value.toString() + " min",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..30f,
            steps = 29,
        )
    }
}
