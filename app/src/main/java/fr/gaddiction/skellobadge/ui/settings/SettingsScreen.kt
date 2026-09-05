package fr.gaddiction.skellobadge.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.PlanningSource
import fr.gaddiction.skellobadge.data.Wording
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.ui.AppViewModel
import fr.gaddiction.skellobadge.ui.Haptics
import fr.gaddiction.skellobadge.ui.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP = DateTimeFormatter.ofPattern("d MMMM 'à' HH'h'mm", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val fromPlanning by viewModel.shiftTypes.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    // La liste de sélection réunit ce que le planning montre aujourd'hui et tout ce que
    // l'application a déjà rencontré : un service saisonnier ne disparaît pas des réglages
    // parce qu'il est hors de la fenêtre courante.
    val allTypes = remember(fromPlanning, settings.knownShiftTypes) {
        (fromPlanning + settings.knownShiftTypes).distinct().sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    TextButton(onClick = { haptics.click(); onBack() }) { Text("Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Expandable(
                title = "Horaires",
                summary = "Avance " + settings.clockInLeadMinutes + " min · bascule " +
                    settings.shiftChangeMaxGapMinutes + " min",
                initiallyExpanded = true,
            ) {
                Stepper(
                    "Avance sur la prise de poste",
                    settings.clockInLeadMinutes,
                    0..30,
                    haptics,
                ) { viewModel.update { s -> s.copy(clockInLeadMinutes = it) } }

                Stepper(
                    "Avance sur le retour de coupure",
                    settings.breakInLeadMinutes,
                    0..30,
                    haptics,
                ) { viewModel.update { s -> s.copy(breakInLeadMinutes = it) } }

                Stepper(
                    "Écart maximal pour un enchaînement",
                    settings.shiftChangeMaxGapMinutes,
                    0..60,
                    haptics,
                    subtitle = "En dessous, deux services collés donnent un seul rappel",
                ) { viewModel.update { s -> s.copy(shiftChangeMaxGapMinutes = it) } }
            }

            Expandable(
                title = "Insistance",
                summary = if (settings.fullScreenAlarmEnabled) {
                    "Relance " + settings.nagIntervalMinutes + " min · alarme à " +
                        settings.fullScreenAlarmAfterMinutes + " min"
                } else {
                    "Relance " + settings.nagIntervalMinutes + " min · sans alarme"
                },
            ) {
                Stepper(
                    "Intervalle entre deux relances",
                    settings.nagIntervalMinutes,
                    0..30,
                    haptics,
                    subtitle = "0 pour ne jamais relancer",
                ) { viewModel.update { s -> s.copy(nagIntervalMinutes = it) } }

                ListItem(
                    headlineContent = { Text("Alarme plein écran") },
                    supportingContent = {
                        Text(
                            "Passé le délai, le rappel s'affiche par-dessus l'écran de " +
                                "verrouillage, sonne en boucle sur le flux des alarmes et " +
                                "vibre jusqu'à ce qu'on y réponde.",
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.fullScreenAlarmEnabled,
                            onCheckedChange = { checked ->
                                haptics.toggle(checked)
                                viewModel.update { it.copy(fullScreenAlarmEnabled = checked) }
                            },
                        )
                    },
                )
                if (settings.fullScreenAlarmEnabled) {
                    Stepper(
                        "Déclenchement de l'alarme après",
                        settings.fullScreenAlarmAfterMinutes,
                        1..60,
                        haptics,
                    ) { viewModel.update { s -> s.copy(fullScreenAlarmAfterMinutes = it) } }
                }
            }

            Expandable(
                title = "Coupure de midi",
                summary = if (settings.lunchFallbackEnabled) "Forcée à 12h et 13h" else "Désactivée",
            ) {
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
                                haptics.toggle(checked)
                                viewModel.update { it.copy(lunchFallbackEnabled = checked) }
                            },
                        )
                    },
                )
            }

            Expandable(
                title = "Formulation des rappels",
                summary = "Titre et texte de chaque type",
            ) {
                Text(
                    "Dans le texte, {heure} sera remplacé par l'heure de l'action et " +
                        "{poste} par le nom du service.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                ReminderKind.entries.forEach { kind ->
                    WordingBlock(kind, settings.wordingFor(kind), haptics) { updated ->
                        viewModel.update { it.copy(wording = it.wording + (kind to updated)) }
                    }
                }
            }

            Expandable(
                title = "Services concernés",
                summary = servicesSummary(allTypes, settings),
            ) {
                Text(
                    "Touche un service pour le couper ou le réactiver. Ceux dont le libellé " +
                        "contient « ou off » sont des journées de réserve : ils restent au " +
                        "planning sans jamais sonner.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                if (allTypes.isEmpty()) {
                    Text(
                        "Aucun service lu pour l'instant.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = {
                            haptics.confirm()
                            viewModel.update { it.copy(disabledShiftTypes = emptySet()) }
                        }) { Text("Tout activer") }

                        TextButton(onClick = {
                            haptics.click()
                            viewModel.update { it.copy(disabledShiftTypes = allTypes.toSet()) }
                        }) { Text("Tout couper") }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        allTypes.forEach { type ->
                            val standby = settings.standbyPatterns.any {
                                it.isNotBlank() && type.lowercase().contains(it.lowercase())
                            }
                            val active = !standby && type !in settings.disabledShiftTypes
                            FilterChip(
                                selected = active,
                                enabled = !standby,
                                onClick = {
                                    haptics.toggle(!active)
                                    viewModel.update { s ->
                                        val disabled = s.disabledShiftTypes.toMutableSet()
                                        if (active) disabled += type else disabled -= type
                                        s.copy(disabledShiftTypes = disabled)
                                    }
                                },
                                label = { Text(type) },
                                leadingIcon = if (active) {
                                    { Text("✓") }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            Expandable(title = "Configuration", summary = settings.targetLabel.ifBlank { "Badgeuse" }) {
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
                    onClick = { haptics.confirm(); viewModel.sendTestReminder() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Envoyer un rappel de test dans 10 s") }

                Text(
                    "Emprunte le même chemin qu'un vrai rappel. Si rien n'arrive, c'est que " +
                        "la surcouche du téléphone bloque les alarmes en veille.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                OutlinedButton(
                    onClick = { haptics.confirm(); viewModel.sendFullScreenTest() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Tester l'alarme plein écran") }

                Text(
                    "Verrouille l'écran pendant les 10 secondes qui suivent : l'alarme doit " +
                        "s'afficher par-dessus, sonner en boucle et vibrer.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                OutlinedButton(
                    onClick = { haptics.click(); viewModel.update { it.copy(configured = false) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Reprendre la configuration") }
            }
        }
    }
}

private fun servicesSummary(all: List<String>, settings: AppSettings): String {
    if (all.isEmpty()) return "Aucun service connu"
    val muted = all.count { type ->
        type in settings.disabledShiftTypes ||
            settings.standbyPatterns.any {
                it.isNotBlank() && type.lowercase().contains(it.lowercase())
            }
    }
    return if (muted == 0) {
        all.size.toString() + " services, tous actifs"
    } else {
        all.size.toString() + " services, " + muted + " sans rappel"
    }
}

/**
 * Section dépliable. L'écran de réglages compte plus de vingt commandes : tout afficher
 * d'un bloc obligerait à faire défiler longuement pour retrouver un réglage précis, alors
 * que le résumé porté par l'en-tête suffit le plus souvent.
 */
@Composable
private fun Expandable(
    title: String,
    summary: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val haptics = rememberHaptics()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.click()
                expanded = !expanded
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(summary, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "⌄",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(rotation),
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) { content() }
    }
}

@Composable
private fun WordingBlock(
    kind: ReminderKind,
    wording: Wording,
    haptics: Haptics,
    onChange: (Wording) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron-wording")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.click()
                open = !open
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(kindLabel(kind), style = MaterialTheme.typography.bodyLarge)
            Text(
                wording.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("⌄", modifier = Modifier.rotate(rotation))
    }

    AnimatedVisibility(visible = open) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = wording.title,
                onValueChange = { onChange(wording.copy(title = it)) },
                label = { Text("Titre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = wording.body,
                onValueChange = { onChange(wording.copy(body = it)) },
                label = { Text("Texte") },
                modifier = Modifier.fillMaxWidth(),
            )
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
    haptics: Haptics,
    subtitle: String? = null,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        FilledTonalButton(
            onClick = {
                haptics.tick()
                onChange((value - 1).coerceIn(range))
            },
            enabled = value > range.first,
        ) { Text("−") }

        Text(
            value.toString() + " min",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )

        FilledTonalButton(
            onClick = {
                haptics.tick()
                onChange((value + 1).coerceIn(range))
            },
            enabled = value < range.last,
        ) { Text("+") }
    }
}
