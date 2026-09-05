package fr.gaddiction.skellobadge.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.domain.Reminder
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.ui.AppViewModel
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)
private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/**
 * Écran de contrôle, pas écran d'usage : il sert à vérifier d'un coup d'œil que les
 * rappels sont bien armés. En régime normal, l'utilisateur ne l'ouvre jamais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, settings: AppSettings, onOpenSettings: () -> Unit) {
    val planning by viewModel.planning.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val now = ZonedDateTime.now()
    // La veille est conservée par le dépôt pour que la journée en cours reste entière
    // après minuit, mais elle n'a rien à faire dans une liste des jours à venir.
    val today = LocalDate.now()
    val days = planning?.days.orEmpty().filterNot { it.date.isBefore(today) }
    val upcoming = PlanningEngine.upcomingReminders(days, now)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badgeuse") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Réglages") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NextReminderCard(upcoming.firstOrNull())
                }

                planning?.error?.takeIf(String::isNotBlank)?.let { error ->
                    item { SyncWarningCard(error, planning?.fromCache == true) }
                }

                item {
                    Text(
                        "Les prochains jours",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(days.take(14), key = { it.date.toString() }) { day ->
                    DayCard(day)
                }

                if (days.isEmpty() && !loading) {
                    item {
                        Text(
                            "Aucun créneau trouvé sur les trois prochaines semaines.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextReminderCard(reminder: Reminder?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Prochain rappel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (reminder == null) {
                Text(
                    "Aucun rappel programmé",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text(
                    DAY.format(reminder.at) + " à " + TIME.format(reminder.at),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    label(reminder.kind) + " · " + reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SyncWarningCard(error: String, fromCache: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (fromCache) {
                    "Planning non rafraîchi, dernière version connue utilisée"
                } else {
                    "Planning indisponible"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DayCard(day: DayPlan) {
    val isOff = day is DayPlan.Off
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOff) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                DAY.format(day.date).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                color = if (isOff) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            when (day) {
                is DayPlan.Off -> Text(
                    day.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is DayPlan.Empty -> Text(
                    "Rien au planning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is DayPlan.Work -> {
                    day.blocks.forEach { block ->
                        Text(
                            TIME.format(block.start) + " – " + TIME.format(block.end) +
                                " · " + block.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    // Une journée coupée porte quatre rappels : sur un écran étroit ils
                    // doivent pouvoir passer à la ligne plutôt que d'être tronqués.
                    FlowRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        day.reminders.forEach { reminder ->
                            Text(
                                shortLabel(reminder.kind) + " " + TIME.format(reminder.at),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun label(kind: ReminderKind): String = when (kind) {
    ReminderKind.CLOCK_IN -> "Prise de poste"
    ReminderKind.BREAK_OUT -> "Départ en coupure"
    ReminderKind.BREAK_IN -> "Retour de coupure"
    ReminderKind.SHIFT_CHANGE -> "Changement de poste"
    ReminderKind.CLOCK_OUT -> "Fin de poste"
}

private fun shortLabel(kind: ReminderKind): String = when (kind) {
    ReminderKind.CLOCK_IN -> "Entrée"
    ReminderKind.BREAK_OUT -> "Pause"
    ReminderKind.BREAK_IN -> "Retour"
    ReminderKind.SHIFT_CHANGE -> "Bascule"
    ReminderKind.CLOCK_OUT -> "Sortie"
}
