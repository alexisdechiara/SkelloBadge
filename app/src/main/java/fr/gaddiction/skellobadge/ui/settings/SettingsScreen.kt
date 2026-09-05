package fr.gaddiction.skellobadge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.PlanningSource
import fr.gaddiction.skellobadge.data.Wording
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP = DateTimeFormatter.ofPattern("d MMMM 'à' HH'h'mm", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val shiftTypes by viewModel.shiftTypes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Section("Horaires")

            Stepper(
                title = "Avance sur la prise de poste",
                subtitle = "Le rappel tombe ce nombre de minutes avant l'heure du service",
                value = settings.clockInLeadMinutes,
                range = 0..30,
                onChange = { viewModel.update { s -> s.copy(clockInLeadMinutes = it) } },
            )
            Stepper(
                title = "Avance sur le retour de coupure",
                value = settings.breakInLeadMinutes,
                range = 0..30,
                onChange = { viewModel.update { s -> s.copy(breakInLeadMinutes = it) } },
            )
            Stepper(
                title = "Écart maximal pour un enchaînement",
                subtitle = "En dessous, deux services collés donnent un seul rappel de bascule",
                value = settings.shiftChangeMaxGapMinutes,
                range = 0..60,
                onChange = { viewModel.update { s -> s.copy(shiftChangeMaxGapMinutes = it) } },
            )

            Section("Insistance")

            Stepper(
                title = "Intervalle entre deux relances",
                subtitle = "0 pour ne jamais relancer",
                value = settings.nagIntervalMinutes,
                range = 0..30,
                onChange = { viewModel.update { s -> s.copy(nagIntervalMinutes = it) } },
            )

            ListItem(
                headlineContent = { Text("Alarme plein écran") },
                supportingContent = {
                    Text(
                        "Passé le délai ci-dessous, le rappel s'affiche par-dessus l'écran " +
                            "de verrouillage, avec le son des alarmes et un bouton vers la " +
                            "badgeuse. Une notification se balaie sans y penser ; pas cet écran.",
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.fullScreenAlarmEnabled,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(fullScreenAlarmEnabled = checked) }
                        },
                    )
                },
            )
            if (settings.fullScreenAlarmEnabled) {
                Stepper(
                    title = "Déclenchement de l'alarme après",
                    value = settings.fullScreenAlarmAfterMinutes,
                    range = 1..60,
                    onChange = {
                        viewModel.update { s -> s.copy(fullScreenAlarmAfterMinutes = it) }
                    },
                )
            }

            Section("Coupure de midi")

            ListItem(
                headlineContent = { Text("Coupure de midi forcée") },
                supportingContent = {
                    Text(
                        "Ajoute un rappel à 12h et 13h sur les journées longues dont le " +
                            "planning ne prévoit aucune coupure.",
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

            Section("Formulation des rappels")
            Text(
                "Fais défiler pour régler chaque type. Dans le texte, {heure} sera remplacé " +
                    "par l'heure de l'action et {poste} par le nom du service.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            WordingCarousel(settings) { kind, wording ->
                viewModel.update { it.copy(wording = it.wording + (kind to wording)) }
            }

            Section("Services concernés")
            Text(
                "Décoche un type pour qu'il ne déclenche plus aucun rappel. Les services " +
                    "dont le libellé contient « ou off » sont déjà traités comme des " +
                    "journées de réserve : ils restent affichés au planning, sans sonner.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (shiftTypes.isEmpty()) {
                Text(
                    "Aucun service lu pour l'instant.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            shiftTypes.forEach { type ->
                val standby = settings.standbyPatterns.any {
                    it.isNotBlank() && type.lowercase().contains(it.lowercase())
                }
                ListItem(
                    headlineContent = { Text(type) },
                    supportingContent = if (standby) {
                        { Text("Journée de réserve : jamais de rappel") }
                    } else {
                        null
                    },
                    leadingContent = {
                        Checkbox(
                            checked = !standby && type !in settings.disabledShiftTypes,
                            enabled = !standby,
                            onCheckedChange = { checked ->
                                viewModel.update { s ->
                                    val disabled = s.disabledShiftTypes.toMutableSet()
                                    if (checked) disabled -= type else disabled += type
                                    s.copy(disabledShiftTypes = disabled)
                                }
                            },
                        )
                    },
                )
            }

            Section("Configuration")

            Info(
                "Source",
                when (settings.source) {
                    PlanningSource.ICS -> settings.icsUrl
                    PlanningSource.DEVICE_CALENDAR ->
                        settings.calendarIds.size.toString() + " calendrier(s)"
                },
            )
            Info(
                "Badgeuse",
                settings.targetLabel.ifBlank { settings.targetUrl }.ifBlank { "Non définie" },
            )
            if (settings.lastSyncEpochMillis > 0) {
                Info(
                    "Dernière synchronisation",
                    STAMP.format(
                        Instant.ofEpochMilli(settings.lastSyncEpochMillis)
                            .atZone(ZoneId.systemDefault()),
                    ),
                )
            }

            OutlinedButton(
                onClick = { viewModel.sendTestReminder() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            ) { Text("Envoyer un rappel de test dans 10 s") }

            Text(
                "Emprunte le même chemin qu'un vrai rappel. Si rien n'arrive, c'est que la " +
                    "surcouche du téléphone bloque les alarmes en veille.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            OutlinedButton(
                onClick = { viewModel.sendFullScreenTest() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            ) { Text("Tester l'alarme plein écran") }

            Text(
                "Verrouille l'écran pendant les 10 secondes qui suivent : l'alarme doit " +
                    "s'afficher par-dessus. Sinon, la permission plein écran est refusée.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            OutlinedButton(
                onClick = { viewModel.update { it.copy(configured = false) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            ) { Text("Reprendre la configuration") }
        }
    }
}

/**
 * Un carrousel de cartes, une par type de rappel. Chacune porte son titre et son texte,
 * modifiables directement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordingCarousel(
    settings: AppSettings,
    onChange: (ReminderKind, Wording) -> Unit,
) {
    val kinds = ReminderKind.entries
    val state = rememberCarouselState { kinds.size }

    HorizontalUncontainedCarousel(
        state = state,
        itemWidth = 300.dp,
        itemSpacing = 12.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) { index ->
        val kind = kinds[index]
        val wording = settings.wordingFor(kind)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    kindLabel(kind),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = wording.title,
                    onValueChange = { onChange(kind, wording.copy(title = it)) },
                    label = { Text("Titre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = wording.body,
                    onValueChange = { onChange(kind, wording.copy(body = it)) },
                    label = { Text("Texte") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun kindLabel(kind: ReminderKind): String = when (kind) {
    ReminderKind.CLOCK_IN -> "Arrivée"
    ReminderKind.BREAK_OUT -> "Départ en coupure"
    ReminderKind.BREAK_IN -> "Retour de coupure"
    ReminderKind.SHIFT_CHANGE -> "Changement de poste"
    ReminderKind.CLOCK_OUT -> "Départ"
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Info(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Réglage en minutes par pas de un.
 *
 * Un curseur serait plus compact, mais il occupe toute la largeur : le doigt qui fait
 * défiler la page en modifie la valeur au passage. Sur un écran qu'on ne consulte
 * qu'exceptionnellement, un réglage changé par accident ne se remarque pas.
 */
@Composable
private fun Stepper(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        FilledTonalButton(
            onClick = { onChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
        ) { Text("−") }

        Text(
            value.toString() + " min",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )

        FilledTonalButton(
            onClick = { onChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
        ) { Text("+") }
    }
}
