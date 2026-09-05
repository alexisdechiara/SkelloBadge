package fr.gaddiction.skellobadge.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.BadgeTarget
import fr.gaddiction.skellobadge.data.InstalledApps
import fr.gaddiction.skellobadge.data.PlanningSource
import fr.gaddiction.skellobadge.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * L'unique écran que l'utilisateur verra vraiment. Il est parcouru une fois, à
 * l'installation ; ensuite l'application n'a plus rien à demander.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: AppViewModel, settings: AppSettings) {
    var step by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf(settings) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stepTitle(step)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    0 -> SourceStep(viewModel, draft) { draft = it }
                    1 -> TargetStep(draft) { draft = it }
                    else -> PermissionsStep()
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { step-- },
                    enabled = step > 0,
                ) { Text("Retour") }

                Button(
                    onClick = {
                        if (step < STEP_COUNT - 1) {
                            step++
                        } else {
                            viewModel.update { draft.copy(configured = true) }
                        }
                    },
                    enabled = canContinue(step, draft),
                ) {
                    Text(if (step < STEP_COUNT - 1) "Continuer" else "Terminer")
                }
            }
        }
    }
}

@Composable
private fun SourceStep(
    viewModel: AppViewModel,
    draft: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current

    Text(
        "D'où vient ton planning ?",
        style = MaterialTheme.typography.headlineSmall,
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = draft.source == PlanningSource.ICS,
            onClick = { onChange(draft.copy(source = PlanningSource.ICS)) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Lien ICS") }
        SegmentedButton(
            selected = draft.source == PlanningSource.DEVICE_CALENDAR,
            onClick = { onChange(draft.copy(source = PlanningSource.DEVICE_CALENDAR)) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Calendrier") }
    }

    when (draft.source) {
        PlanningSource.ICS -> {
            val check by viewModel.linkCheck.collectAsStateWithLifecycle()

            OutlinedTextField(
                value = draft.icsUrl,
                onValueChange = {
                    viewModel.resetLinkCheck()
                    onChange(draft.copy(icsUrl = it.trim()))
                },
                label = { Text("Adresse du flux ICS") },
                placeholder = { Text("https://api.skello.io/users/.../feeds/ics/....ics") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Dans Skello : Mon compte, puis le lien d'abonnement au planning. " +
                    "L'application le relit toute seule et garde une copie hors ligne.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(
                onClick = { viewModel.verifyIcsLink(draft.icsUrl) },
                enabled = draft.icsUrl.startsWith("http") &&
                    check !is AppViewModel.LinkCheck.Running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Vérifier le lien") }

            when (val result = check) {
                AppViewModel.LinkCheck.Running -> Text(
                    "Vérification en cours...",
                    style = MaterialTheme.typography.bodyMedium,
                )

                is AppViewModel.LinkCheck.Ok -> Text(
                    "Planning lu : " + result.shifts + " créneaux et " +
                        result.daysOff + " jours non travaillés reconnus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                is AppViewModel.LinkCheck.Failed -> Text(
                    "Lien inutilisable : " + result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                AppViewModel.LinkCheck.Idle -> Unit
            }
        }

        PlanningSource.DEVICE_CALENDAR -> {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            val calendars = remember(draft.source) {
                fr.gaddiction.skellobadge.data.calendar.DeviceCalendarSource(context).calendars()
            }
            if (calendars.isEmpty()) {
                Text("Autorise l'accès au calendrier pour choisir celui du travail.")
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                ) { Text("Autoriser le calendrier") }
            } else {
                calendars.forEach { calendar ->
                    ListItem(
                        headlineContent = { Text(calendar.displayName) },
                        supportingContent = { Text(calendar.accountName) },
                        leadingContent = {
                            Checkbox(
                                checked = calendar.id in draft.calendarIds,
                                onCheckedChange = { checked ->
                                    val ids = draft.calendarIds.toMutableSet()
                                    if (checked) ids += calendar.id else ids -= calendar.id
                                    onChange(draft.copy(calendarIds = ids))
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetStep(draft: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current

    Text(
        "Qu'ouvre-t-on quand tu touches la notification ?",
        style = MaterialTheme.typography.headlineSmall,
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = draft.targetKind == BadgeTarget.APP,
            onClick = { onChange(draft.copy(targetKind = BadgeTarget.APP)) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Une application") }
        SegmentedButton(
            selected = draft.targetKind == BadgeTarget.URL,
            onClick = { onChange(draft.copy(targetKind = BadgeTarget.URL)) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Une adresse web") }
    }

    when (draft.targetKind) {
        BadgeTarget.APP -> {
            var query by remember { mutableStateOf("skello") }
            val apps by produceState(initialValue = emptyList<InstalledApps.Entry>()) {
                value = withContext(Dispatchers.IO) { InstalledApps.launchable(context) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher une application") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
            if (filtered.isEmpty()) {
                Text(
                    when {
                        apps.isEmpty() -> "Lecture des applications installées..."
                        else -> "Aucune application ne correspond. Efface la recherche " +
                            "pour voir la liste complète, ou choisis une adresse web."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                return
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(filtered, key = { it.packageName }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.label) },
                            supportingContent = {
                                Text(entry.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = draft.targetPackage == entry.packageName,
                                    onClick = {
                                        onChange(
                                            draft.copy(
                                                targetPackage = entry.packageName,
                                                targetLabel = entry.label,
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        BadgeTarget.URL -> {
            OutlinedTextField(
                value = draft.targetUrl,
                onValueChange = { onChange(draft.copy(targetUrl = it.trim())) },
                label = { Text("Adresse de la badgeuse") },
                placeholder = { Text("https://app.skello.io/...") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Text("Deux autorisations, une fois", style = MaterialTheme.typography.headlineSmall)

    Text(
        "Les rappels doivent pouvoir sonner à l'heure exacte, y compris quand le " +
            "téléphone dort. Sans l'exemption d'économie de batterie, certains " +
            "constructeurs suspendent les alarmes en veille prolongée.",
        style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(Modifier.height(4.dp))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OutlinedButton(
            onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Autoriser les notifications") }
    }

    OutlinedButton(
        onClick = { context.requestIgnoreBatteryOptimizations() },
        enabled = !context.isIgnoringBatteryOptimizations(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (context.isIgnoringBatteryOptimizations()) {
                "Batterie déjà configurée"
            } else {
                "Exempter de l'économie de batterie"
            },
        )
    }
}

private const val STEP_COUNT = 3

private fun stepTitle(step: Int): String = when (step) {
    0 -> "Planning"
    1 -> "Badgeuse"
    else -> "Autorisations"
}

private fun canContinue(step: Int, draft: AppSettings): Boolean = when (step) {
    0 -> when (draft.source) {
        PlanningSource.ICS -> draft.icsUrl.startsWith("http")
        PlanningSource.DEVICE_CALENDAR -> draft.calendarIds.isNotEmpty()
    }

    1 -> when (draft.targetKind) {
        BadgeTarget.APP -> draft.targetPackage.isNotBlank()
        BadgeTarget.URL -> draft.targetUrl.startsWith("http")
    }

    else -> true
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return power.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.requestIgnoreBatteryOptimizations() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + packageName)),
        )
    }
}
