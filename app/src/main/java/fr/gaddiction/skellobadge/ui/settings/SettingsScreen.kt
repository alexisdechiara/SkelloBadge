package fr.gaddiction.skellobadge.ui.settings

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.Contacts
import fr.gaddiction.skellobadge.data.PlanningSource
import fr.gaddiction.skellobadge.data.Sound
import fr.gaddiction.skellobadge.data.Tone
import fr.gaddiction.skellobadge.data.UpdateChecker
import fr.gaddiction.skellobadge.data.UpdateInstaller
import fr.gaddiction.skellobadge.data.Wording
import fr.gaddiction.skellobadge.domain.ReleaseNotes
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.notify.SoundPreview
import fr.gaddiction.skellobadge.notify.Sounds
import fr.gaddiction.skellobadge.ui.AppViewModel
import fr.gaddiction.skellobadge.ui.Haptics
import fr.gaddiction.skellobadge.ui.ReleaseNotesList
import fr.gaddiction.skellobadge.ui.UpdateInstallPanel
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
    val update by viewModel.update.collectAsStateWithLifecycle()
    val install by viewModel.install.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current

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
                    settings.clockOutLeadMinutes + " min avant · pause dès " +
                    settings.breakMinGapMinutes + " min",
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
                    "Écart minimal pour une pause",
                    settings.breakMinGapMinutes,
                    0..60,
                    haptics,
                    subtitle = "En dessous, deux services qui s'enchaînent ne donnent " +
                        "qu'un rappel groupé",
                ) { viewModel.update { s -> s.copy(breakMinGapMinutes = it) } }
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
                // L'interrupteur en tête : ce qu'il fait apparaître se place en dessous,
                // de sorte que le reste de la section ne se déplace pas quand on le bascule.
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

                Stepper(
                    "Intervalle entre deux relances",
                    settings.nagIntervalMinutes,
                    0..30,
                    haptics,
                    subtitle = "0 pour ne jamais relancer",
                ) { viewModel.update { s -> s.copy(nagIntervalMinutes = it) } }
            }

            Section(
                title = "Sons",
                summary = "Rappels : " + Sounds.label(context, settings.reminderSound) +
                    if (settings.fullScreenAlarmEnabled) {
                        " · alarme : " + Sounds.label(context, settings.alarmSound)
                    } else {
                        ""
                    },
            ) {
                SoundField(
                    title = "Son des rappels",
                    current = settings.reminderSound,
                    forAlarm = false,
                    haptics = haptics,
                ) { sound -> viewModel.update { it.copy(reminderSound = sound) } }

                if (settings.fullScreenAlarmEnabled) {
                    SoundField(
                        title = "Son de l'alarme plein écran",
                        current = settings.alarmSound,
                        forAlarm = true,
                        haptics = haptics,
                    ) { sound -> viewModel.update { it.copy(alarmSound = sound) } }
                }

                Text(
                    "Le son des rappels vaut pour tous leurs types, et remplace celui qui " +
                        "aurait été réglé type par type dans les paramètres Android. " +
                        "L'alarme sonne au volume des alarmes, sauf en mode vibreur ou " +
                        "silencieux ; la vibration, elle, part toujours.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            Section(
                title = "Pause",
                summary = pauseSummary(settings),
            ) {
                ListItem(
                    headlineContent = { Text("Lire la pause dans les notes") },
                    supportingContent = {
                        Text(
                            "« Pause cantine 12h-13h », « Repas 11h45 → 12h30 », ou le trou " +
                                "entre deux animations datées : l'heure écrite par le " +
                                "responsable prime sur la pause forcée.",
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.notePauseEnabled,
                            onCheckedChange = { checked ->
                                haptics.toggle(checked)
                                viewModel.update { it.copy(notePauseEnabled = checked) }
                            },
                        )
                    },
                )

                if (settings.notePauseEnabled) {
                    KeywordsField(settings.notePauseKeywords) { keywords ->
                        viewModel.update { it.copy(notePauseKeywords = keywords) }
                    }
                }

                ListItem(
                    headlineContent = { Text("Pause forcée") },
                    supportingContent = {
                        Text(
                            "Ajoute un départ et un retour sur les journées longues dont ni " +
                                "le planning ni la note ne prévoient de pause.",
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
                title = "Journées à confirmer",
                summary = when {
                    !settings.standbyAskEnabled -> "Sans demande la veille"
                    settings.contactName.isBlank() -> "Demande la veille · contact à choisir"
                    else -> "Demande à " + settings.contactName + " la veille"
                },
            ) {
                ListItem(
                    headlineContent = { Text("Demander la veille") },
                    supportingContent = {
                        Text(
                            "Les services dont le libellé annonce une alternative — « EG ou " +
                                "Bureau », « EG ou Off » — ne sont fixés que la veille. Un " +
                                "rappel te fait penser à poser la question.",
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.standbyAskEnabled,
                            onCheckedChange = { checked ->
                                haptics.toggle(checked)
                                viewModel.update { it.copy(standbyAskEnabled = checked) }
                            },
                        )
                    },
                )

                if (settings.standbyAskEnabled) {
                    ContactPicker(settings, haptics) { entry ->
                        viewModel.update {
                            it.copy(contactName = entry.name, contactNumber = entry.number)
                        }
                    }

                    TimeField(
                        label = "Si tu travailles la veille",
                        minutes = settings.askWorkingMinutes,
                        haptics = haptics,
                    ) { viewModel.update { s -> s.copy(askWorkingMinutes = it) } }

                    TimeField(
                        label = "Si tu es en repos la veille",
                        minutes = settings.askRestMinutes,
                        haptics = haptics,
                    ) { viewModel.update { s -> s.copy(askRestMinutes = it) } }

                    Text(
                        "Une seule demande par série : deux journées à confirmer qui se " +
                            "suivent relèvent du même évènement, sauf si leurs descriptions " +
                            "diffèrent au planning.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
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

                OutlinedButton(
                    onClick = { haptics.confirm(); viewModel.sendConfirmTest() },
                    enabled = settings.contactNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("Envoyer une demande de confirmation") }

                Text(
                    if (settings.contactNumber.isBlank()) {
                        "Choisis d'abord un responsable dans « Journées à confirmer »."
                    } else {
                        "Touche la notification : la messagerie doit s'ouvrir sur " +
                            settings.contactName.ifBlank { settings.contactNumber } + "."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                Text(
                    "Les trois arrivent au bout d'une seconde et empruntent le chemin complet " +
                        "d'un vrai rappel. L'alarme s'ouvre directement, sans avoir à " +
                        "verrouiller l'écran.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            Section(
                title = "Mises à jour",
                summary = "Version " + viewModel.installedVersion.ifBlank { "inconnue" } +
                    if (settings.updateCheckEnabled) " · vérifiée chaque jour" else "",
            ) {
                ListItem(
                    headlineContent = { Text("Vérifier automatiquement") },
                    supportingContent = {
                        Text(
                            "Une fois par jour, cherche une nouvelle version parmi les " +
                                "publications GitHub et l'annonce par une notification discrète.",
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.updateCheckEnabled,
                            onCheckedChange = { checked ->
                                haptics.toggle(checked)
                                viewModel.update { it.copy(updateCheckEnabled = checked) }
                            },
                        )
                    },
                )

                UpdateStatus(
                    state = update,
                    installState = install,
                    haptics = haptics,
                    onCheck = { viewModel.checkForUpdate(manual = true) },
                    onInstall = viewModel::installUpdate,
                    onAllow = {
                        runCatching { context.startActivity(viewModel.installPermissionIntent()) }
                    },
                    onOpen = { url ->
                        runCatching { context.startActivity(UpdateChecker.downloadIntent(url)) }
                    },
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
 * Choix du responsable dans les contacts du téléphone.
 *
 * Le sélecteur système n'accorde l'accès qu'au seul contact retenu : l'application n'a
 * jamais besoin de la permission de lire l'ensemble du carnet d'adresses.
 */
@Composable
private fun ContactPicker(
    settings: AppSettings,
    haptics: Haptics,
    onPicked: (Contacts.Entry) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data
                ?.let { Contacts.read(context, it) }
                ?.let(onPicked)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Responsable à contacter", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (settings.contactName.isBlank()) {
                    "Aucun contact choisi. Sans lui, le rappel ouvre l'application."
                } else {
                    settings.contactName + " · " + settings.contactNumber
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FilledTonalButton(
            onClick = {
                haptics.click()
                runCatching { picker.launch(Contacts.pickIntent()) }
            },
        ) { Text(if (settings.contactName.isBlank()) "Choisir" else "Changer") }
    }
}

/**
 * Choix d'un son. La liste s'ouvre dans une fenêtre où chaque son se fait entendre au
 * toucher, sur le flux où il retentira : on choisit à l'oreille, pas sur un nom.
 */
@Composable
private fun SoundField(
    title: String,
    current: Sound,
    forAlarm: Boolean,
    haptics: Haptics,
    onChange: (Sound) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.let {
                    IntentCompat.getParcelableExtra(
                        it,
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        Uri::class.java,
                    )
                }
                ?.let { onChange(Sound.Picked(it.toString())) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(Sounds.label(context, current), style = MaterialTheme.typography.bodySmall)
        }
        FilledTonalButton(onClick = { haptics.click(); open = true }) { Text("Changer") }
    }

    if (open) {
        var selected by remember { mutableStateOf(current) }
        val close = {
            SoundPreview.stop()
            open = false
        }
        // Le son en cours d'écoute ne doit pas survivre à la fenêtre qui l'a lancé.
        DisposableEffect(Unit) { onDispose { SoundPreview.stop() } }

        val choices = remember(current) {
            Tone.entries.map { Sound.Bundled(it) } + Sound.Phone +
                listOfNotNull(current as? Sound.Picked)
        }

        AlertDialog(
            onDismissRequest = close,
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { sound ->
                        SoundChoice(
                            label = Sounds.label(context, sound),
                            description = when (sound) {
                                is Sound.Bundled -> sound.tone.description
                                Sound.Phone ->
                                    if (forAlarm) "Celui du réveil" else "Celui des notifications"
                                is Sound.Picked -> "Choisi dans le téléphone"
                            },
                            selected = sound == selected,
                        ) {
                            haptics.tick()
                            selected = sound
                            SoundPreview.play(context, sound, forAlarm)
                        }
                    }
                    TextButton(
                        onClick = {
                            haptics.click()
                            SoundPreview.stop()
                            runCatching { picker.launch(Sounds.pickerIntent(current, forAlarm)) }
                            open = false
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("Autre son du téléphone…") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    if (selected != current) onChange(selected)
                    close()
                }) { Text("Choisir ce son") }
            },
            dismissButton = {
                TextButton(onClick = { haptics.click(); close() }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun SoundChoice(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
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

/**
 * Recherche à la demande, et son résultat. Elle ignore un « Plus tard » donné sur
 * l'accueil : qui vient chercher une mise à jour veut la voir.
 */
@Composable
private fun UpdateStatus(
    state: AppViewModel.UpdateState,
    installState: UpdateInstaller.State,
    haptics: Haptics,
    onCheck: () -> Unit,
    onInstall: (UpdateChecker.Release) -> Unit,
    onAllow: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val result = (state as? AppViewModel.UpdateState.Done)?.result
    val message = when {
        state == AppViewModel.UpdateState.Checking -> "Recherche en cours…"
        result is UpdateChecker.Result.UpToDate -> "Tu as la dernière version."
        result is UpdateChecker.Result.Available ->
            "La version " + result.release.version + " est disponible. Elle s'installe " +
                "par-dessus l'actuelle, sans perdre ta configuration."
        result is UpdateChecker.Result.Failed -> "Vérification impossible : " + result.reason + "."
        else -> null
    }
    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = if (result is UpdateChecker.Result.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    if (result is UpdateChecker.Result.Available) {
        val release = result.release
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            if (ReleaseNotes.hasContent(release.notes)) {
                ReleaseNotesList(release.notes, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            UpdateInstallPanel(
                release = release,
                state = installState,
                textColor = MaterialTheme.colorScheme.onSurface,
                haptics = haptics,
                onInstall = { onInstall(release) },
                onAllow = onAllow,
                onBrowser = { onOpen(release.downloadUrl) },
            ) {
                TextButton(onClick = { haptics.click(); onOpen(release.pageUrl) }) {
                    Text("Page de la version")
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = { haptics.click(); onCheck() },
            enabled = state != AppViewModel.UpdateState.Checking,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) { Text("Rechercher une mise à jour") }
    }
}

private fun pauseSummary(settings: AppSettings): String {
    val fallback = if (settings.lunchFallbackEnabled) {
        "sinon " + formatMinutes(settings.lunchStartMinutes) +
            " – " + formatMinutes(settings.lunchEndMinutes)
    } else {
        null
    }
    return when {
        settings.notePauseEnabled && fallback != null -> "Lue dans la note, $fallback"
        settings.notePauseEnabled -> "Lue dans la note"
        fallback != null -> "Forcée de " + formatMinutes(settings.lunchStartMinutes) +
            " à " + formatMinutes(settings.lunchEndMinutes)
        else -> "Désactivée"
    }
}

/**
 * Liste de mots saisie d'un bloc, séparés par des virgules.
 *
 * Le texte vit dans un état local : reconstruit à chaque frappe depuis l'ensemble
 * enregistré, il perdrait la virgule qu'on vient de taper avant le mot suivant.
 */
@Composable
private fun KeywordsField(keywords: Set<String>, onChange: (Set<String>) -> Unit) {
    var text by remember { mutableStateOf(keywords.sorted().joinToString(", ")) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            onChange(
                value.split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet(),
            )
        },
        label = { Text("Mots qui signalent la pause") },
        supportingText = {
            Text("Séparés par des virgules ; majuscules et accents sont ignorés.")
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    )
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
    ReminderKind.STANDBY_CONFIRM -> "Réserve à confirmer"
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
