package fr.gaddiction.skellobadge.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.R
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

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    settings: AppSettings,
    onReconfigure: () -> Unit,
    onBack: () -> Unit,
) {
    val fromPlanning by viewModel.shiftTypes.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    // La liste réunit ce que le planning montre aujourd'hui et tout ce que l'application a
    // déjà rencontré : un service saisonnier ne disparaît pas des réglages parce qu'il est
    // hors de la fenêtre courante.
    val allTypes = remember(fromPlanning, settings.knownShiftTypes) {
        (fromPlanning + settings.knownShiftTypes).distinct().sorted()
    }
    val standbyTypes = allTypes.filter { settings.isStandby(it) }
    val selectableTypes = allTypes - standbyTypes.toSet()

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
            Section(
                title = "Horaires",
                summary = "Entrées " + settings.clockInLeadMinutes + " min avant · sorties " +
                    settings.clockOutLeadMinutes + " min avant",
                initiallyExpanded = true,
            ) {
                Stepper(
                    "Avance sur la prise de poste",
                    settings.clockInLeadMinutes,
                    0..30,
                    haptics,
                ) { viewModel.update { s -> s.copy(clockInLeadMinutes = it) } }

                Stepper(
                    "Avance sur le départ en pause",
                    settings.breakOutLeadMinutes,
                    0..30,
                    haptics,
                ) { viewModel.update { s -> s.copy(breakOutLeadMinutes = it) } }

                Stepper(
                    "Avance sur le retour de pause",
                    settings.breakInLeadMinutes,
                    0..30,
                    haptics,
                ) { viewModel.update { s -> s.copy(breakInLeadMinutes = it) } }

                Stepper(
                    "Avance sur la fin de poste",
                    settings.clockOutLeadMinutes,
                    0..30,
                    haptics,
                    subtitle = "0 pour être rappelé à l'heure pile",
                ) { viewModel.update { s -> s.copy(clockOutLeadMinutes = it) } }

                Stepper(
                    "Écart maximal pour un enchaînement",
                    settings.shiftChangeMaxGapMinutes,
                    0..60,
                    haptics,
                    subtitle = "En dessous, deux services collés donnent un seul rappel",
                ) { viewModel.update { s -> s.copy(shiftChangeMaxGapMinutes = it) } }
            }

            Section(
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
                                "verrouillage, sonne en boucle et vibre jusqu'à ce que tu " +
                                "y répondes.",
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

            Section(
                title = "Pause",
                summary = if (settings.lunchFallbackEnabled) {
                    "Forcée de " + formatMinutes(settings.lunchStartMinutes) +
                        " à " + formatMinutes(settings.lunchEndMinutes)
                } else {
                    "Désactivée"
                },
            ) {
                ListItem(
                    headlineContent = { Text("Pause forcée") },
                    supportingContent = {
                        Text(
                            "Ajoute un départ et un retour sur les journées longues dont le " +
                                "planning ne prévoit aucune pause.",
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

                if (settings.lunchFallbackEnabled) {
                    TimeField(
                        label = "Début de la pause",
                        minutes = settings.lunchStartMinutes,
                        haptics = haptics,
                    ) { viewModel.update { s -> s.copy(lunchStartMinutes = it) } }

                    TimeField(
                        label = "Fin de la pause",
                        minutes = settings.lunchEndMinutes,
                        haptics = haptics,
                    ) { viewModel.update { s -> s.copy(lunchEndMinutes = it) } }

                    if (settings.lunchEndMinutes <= settings.lunchStartMinutes) {
                        Text(
                            "La fin doit être postérieure au début, sans quoi aucun rappel de " +
                                "pause ne sera posé.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Section(
                title = "Texte des rappels",
                summary = "Titre et texte de chaque type",
            ) {
                WordingEditor(
                    settings = settings,
                    haptics = haptics,
                    onReset = { viewModel.update { it.copy(wording = Wording.DEFAULTS) } },
                    onChange = { kind, wording ->
                        viewModel.update { it.copy(wording = it.wording + (kind to wording)) }
                    },
                )
            }

            Section(
                title = "Services concernés",
                summary = servicesSummary(selectableTypes, settings),
            ) {
                ServiceSelector(
                    types = selectableTypes,
                    disabled = settings.disabledShiftTypes,
                    haptics = haptics,
                    onToggle = { type, active ->
                        viewModel.update { s ->
                            val muted = s.disabledShiftTypes.toMutableSet()
                            if (active) muted -= type else muted += type
                            s.copy(disabledShiftTypes = muted)
                        }
                    },
                    onAll = { active ->
                        viewModel.update { s ->
                            s.copy(
                                disabledShiftTypes = if (active) emptySet() else selectableTypes.toSet(),
                            )
                        }
                    },
                )

                if (standbyTypes.isNotEmpty()) {
                    Text(
                        "Journées de réserve, jamais de rappel : " +
                            standbyTypes.joinToString(", ") +
                            ". Sur la carte du jour, un bouton permet de les réactiver " +
                            "quand tu es finalement appelé.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            Section(
                title = "Tests",
                summary = "Vérifier que ce téléphone laisse passer les rappels",
            ) {
                Text(
                    "À faire une fois sur chaque téléphone avant d'en dépendre. Certaines " +
                        "surcouches constructeur étouffent les alarmes en veille sans rien dire.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                OutlinedButton(
                    onClick = { haptics.confirm(); viewModel.sendTestReminder() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Envoyer une notification de test") }

                OutlinedButton(
                    onClick = { haptics.confirm(); viewModel.sendFullScreenTest() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Déclencher l'alarme plein écran") }

                Text(
                    "Les deux arrivent au bout d'une seconde et empruntent le chemin complet " +
                        "d'un vrai rappel. L'alarme s'ouvre directement, sans avoir à " +
                        "verrouiller l'écran.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            Section(
                title = "Configuration",
                summary = settings.targetLabel.ifBlank { "Badgeuse" },
            ) {
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
                    onClick = { haptics.click(); onReconfigure() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Changer de planning ou de badgeuse") }

                Text(
                    "Rouvre les trois écrans d'installation. Rien n'est modifié tant que tu " +
                        "n'as pas validé ; tu peux en sortir à tout moment.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/**
 * Heure réglable, ouverte sur le sélecteur Material plutôt que sur deux compteurs :
 * choisir « 12 h 15 » doit rester un geste, pas quinze appuis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    haptics: Haptics,
    onChange: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { haptics.click(); open = true }) {
            Text(formatMinutes(minutes))
        }
    }

    if (open) {
        val state = rememberTimePickerState(
            initialHour = minutes / 60,
            initialMinute = minutes % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    onChange(state.hour * 60 + state.minute)
                    open = false
                }) { Text("Enregistrer l'heure") }
            },
            dismissButton = {
                TextButton(onClick = { haptics.click(); open = false }) { Text("Annuler") }
            },
        )
    }
}

private fun formatMinutes(minutes: Int): String =
    (minutes / 60).toString().padStart(2, '0') + " h " +
        (minutes % 60).toString().padStart(2, '0')

/**
 * Sélection multiple en liste déroulante.
 *
 * Des puces à plat étaient lisibles tant que les services se comptaient sur une main ;
 * l'établissement en accumulant de nouveaux au fil des mois, elles finiraient par occuper
 * plusieurs écrans. La liste déroulante garde une hauteur constante quel qu'en soit le
 * nombre, et le champ résume l'état sans avoir à l'ouvrir.
 */
@Composable
private fun ServiceSelector(
    types: List<String>,
    disabled: Set<String>,
    haptics: Haptics,
    onToggle: (String, Boolean) -> Unit,
    onAll: (Boolean) -> Unit,
) {
    if (types.isEmpty()) {
        Text(
            "Aucun service connu. La liste se remplira à la première synchronisation.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val active = types.count { it !in disabled }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        OutlinedButton(
            onClick = {
                haptics.click()
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (active <= 1) {
                    active.toString() + " service sur " + types.size + " déclenche des rappels"
                } else {
                    active.toString() + " services sur " + types.size + " déclenchent des rappels"
                },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
            )
        }

        // Le menu reste à hauteur constante et défile de lui-même : la liste peut grossir
        // sans jamais repousser le reste des réglages hors de l'écran. Les deux actions
        // globales y figurent en tête plutôt qu'à l'extérieur : elles agissent sur les
        // mêmes éléments, elles doivent vivre au même endroit.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Tout activer") },
                enabled = active < types.size,
                onClick = { haptics.confirm(); onAll(true) },
            )
            DropdownMenuItem(
                text = { Text("Tout désactiver") },
                enabled = active > 0,
                onClick = { haptics.click(); onAll(false) },
            )
            HorizontalDivider()

            types.forEach { type ->
                val checked = type !in disabled
                DropdownMenuItem(
                    text = { Text(type) },
                    leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                    onClick = {
                        haptics.toggle(!checked)
                        onToggle(type, !checked)
                    },
                )
            }
        }
    }
}

/**
 * Éditeur de formulation à un seul niveau : on choisit le type par une puce, puis on
 * modifie ses deux champs juste en dessous. Empiler un dépliage par type à l'intérieur
 * d'une section déjà dépliable obligeait à deux gestes pour atteindre un champ.
 */
@Composable
private fun WordingEditor(
    settings: AppSettings,
    haptics: Haptics,
    onReset: () -> Unit,
    onChange: (ReminderKind, Wording) -> Unit,
) {
    var selected by remember { mutableStateOf(ReminderKind.CLOCK_IN) }
    val wording = settings.wordingFor(selected)

    // Une seule ligne qui déborde et défile, plutôt qu'un retour à la ligne : les cinq
    // types gardent un ordre stable, et la hauteur du bloc ne change pas selon la largeur
    // de l'écran ou la longueur des libellés.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReminderKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = {
                    haptics.click()
                    selected = kind
                },
                label = { Text(kindLabel(kind)) },
            )
        }
    }

    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = wording.title,
            onValueChange = { onChange(selected, wording.copy(title = it)) },
            label = { Text("Titre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = wording.body,
            onValueChange = { onChange(selected, wording.copy(body = it)) },
            label = { Text("Texte") },
            supportingText = {
                Text("{heure} devient l'heure de l'action, {poste} le nom du service")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Les textes sont enregistrés dès la première ouverture des réglages : sans ce
        // bouton, une amélioration des formulations par défaut ne parviendrait jamais à
        // qui a déjà installé l'application.
        if (settings.wording != Wording.DEFAULTS) {
            TextButton(onClick = { haptics.click(); onReset() }) {
                Text("Rétablir les textes par défaut")
            }
        }
    }
}

private fun servicesSummary(types: List<String>, settings: AppSettings): String {
    if (types.isEmpty()) return "Aucun service connu"
    val muted = types.count { it in settings.disabledShiftTypes }
    val total = types.size.toString() + if (types.size <= 1) " service" else " services"
    return when (muted) {
        0 -> "$total, tous actifs"
        1 -> "$total, 1 sans rappel"
        else -> "$total, $muted sans rappel"
    }
}

/**
 * Section dépliable. L'écran de réglages compte plus de vingt commandes : tout afficher
 * d'un bloc obligerait à faire défiler longuement pour retrouver un réglage précis, alors
 * que le résumé porté par l'en-tête suffit le plus souvent.
 */
@Composable
private fun Section(
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
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = if (expanded) "Replier" else "Déplier",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(rotation),
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) { content() }
    }
}

/** Mêmes termes que sur l'écran de planning et dans les canaux Android. */
private fun kindLabel(kind: ReminderKind): String = when (kind) {
    ReminderKind.CLOCK_IN -> "Entrée"
    ReminderKind.BREAK_OUT -> "Départ en pause"
    ReminderKind.BREAK_IN -> "Retour de pause"
    ReminderKind.SHIFT_CHANGE -> "Changement de poste"
    ReminderKind.CLOCK_OUT -> "Sortie"
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
