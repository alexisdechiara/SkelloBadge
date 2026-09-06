package fr.gaddiction.skellobadge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gaddiction.skellobadge.R
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.FetchErrors
import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.domain.Reminder
import fr.gaddiction.skellobadge.domain.ReminderKind
import fr.gaddiction.skellobadge.domain.WorkBlock
import fr.gaddiction.skellobadge.ui.AppViewModel
import fr.gaddiction.skellobadge.ui.rememberHaptics
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)
private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

/**
 * Écran de contrôle, pas écran d'usage : il sert à vérifier d'un coup d'œil que les
 * rappels sont bien armés. En régime normal, l'utilisateur ne l'ouvre jamais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, settings: AppSettings, onOpenSettings: () -> Unit) {
    val planning by viewModel.planning.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    val now = ZonedDateTime.now()
    // La veille est conservée par le dépôt pour que la journée en cours reste entière
    // après minuit, mais elle n'a rien à faire dans une liste des jours à venir.
    val today = LocalDate.now()
    val days = planning?.days.orEmpty().filterNot { it.date.isBefore(today) }.take(21)
    val upcoming = PlanningEngine.upcomingReminders(days, now)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResourceShort()) },
                actions = {
                    TextButton(onClick = { haptics.click(); onOpenSettings() }) {
                        Text("Réglages")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { NextReminderCard(upcoming.firstOrNull(), now) }

                planning?.error?.takeIf(String::isNotBlank)?.let { error ->
                    item { SyncWarningCard(error, planning?.fromCache == true) }
                }

                if (days.isEmpty() && !loading) {
                    item {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                "Rien au planning pour les trois prochaines semaines.",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Si tu attendais des services, vérifie le lien du planning " +
                                    "dans Réglages.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Un intertitre à chaque changement de semaine : sur trois semaines de
                // planning, c'est le repère qui permet de se situer sans compter les jours.
                var lastWeekStart: LocalDate? = null
                days.forEach { day ->
                    val weekStart = day.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    if (weekStart != lastWeekStart) {
                        lastWeekStart = weekStart
                        item(key = "week-" + weekStart) { WeekHeader(weekStart, today) }
                    }
                    item(key = day.date.toString()) {
                        DayCard(
                            day = day,
                            today = today,
                            declaredWorking = settings.worksOn(day.date),
                            onToggleWorking = { viewModel.toggleWorkingDay(day.date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stringResourceShort(): String =
    androidx.compose.ui.res.stringResource(R.string.app_name_short)

@Composable
private fun WeekHeader(weekStart: LocalDate, today: LocalDate) {
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val label = when (ChronoUnit.WEEKS.between(thisWeek, weekStart)) {
        0L -> "Cette semaine"
        1L -> "La semaine prochaine"
        else -> "Semaine du " + DAY_SHORT.format(weekStart)
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun NextReminderCard(reminder: Reminder?, now: ZonedDateTime) {
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
                    "Aucun rappel à venir",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Ils apparaîtront dès que ton planning contiendra un service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                return@Card
            }

            // Le compte à rebours dit en un mot ce qu'une date complète oblige à calculer.
            Text(
                countdown(now, reminder.at),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            // Date brute : le compte à rebours au-dessus porte déjà « demain » ou
            // « dans 2 jours », et le répéter ici ne dirait rien de plus.
            Text(
                DAY.format(reminder.at).replaceFirstChar { it.uppercase() } +
                    " à " + TIME.format(reminder.at),
                style = MaterialTheme.typography.titleMedium,
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
                    "Planning non rafraîchi"
                } else {
                    "Planning non récupéré"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                if (fromCache) {
                    "Les rappels reposent sur la dernière version connue. " +
                        FetchErrors.explain(error)
                } else {
                    "Aucun rappel ne sera posé tant que le planning reste inaccessible. " +
                        FetchErrors.explain(error)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            // Cause technique, reléguée : utile pour diagnostiquer, inutile pour agir.
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DayCard(
    day: DayPlan,
    today: LocalDate,
    declaredWorking: Boolean,
    onToggleWorking: () -> Unit,
) {
    val isToday = day.date == today
    val isRest = day is DayPlan.Off || day is DayPlan.Empty
    val haptics = rememberHaptics()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isToday -> MaterialTheme.colorScheme.secondaryContainer
                isRest -> MaterialTheme.colorScheme.surfaceContainerLowest
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayLabel(day.date, today).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    color = if (isRest) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                (day as? DayPlan.Work)?.let { HoursBadge(it.blocks) }
            }

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
                    day.blocks.forEach { block -> BlockRow(block) }

                    if (day.reminders.isEmpty()) {
                        Text(
                            "Journée de réserve · aucun rappel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // Le jour où l'on est appelé en renfort est celui où un oubli coûte
                        // le plus cher : il faut pouvoir rétablir les rappels d'un geste.
                        //
                        // Bouton plein plutôt que tonal : sur la carte du jour, qui a déjà
                        // un fond teinté, un bouton tonal s'y confond et cesse de se lire
                        // comme une commande.
                        Button(
                            onClick = { haptics.confirm(); onToggleWorking() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Je travaille ce jour") }
                    } else {
                        if (declaredWorking) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(
                                    "Rappels rétablis pour ce jour",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { haptics.click(); onToggleWorking() }) {
                                    Text("Annuler")
                                }
                            }
                        }
                        // Une journée coupée porte quatre rappels : sur un écran étroit ils
                        // doivent pouvoir passer à la ligne plutôt que d'être tronqués.
                        FlowRow(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            day.reminders.forEach { ReminderChip(it) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Une ligne par créneau. La note du planning n'y figure pas : elle atteint parfois dix
 * lignes de consignes et de prénoms, ce qui noyait la journée. Elle reste là où elle est
 * utile, dans la notification du moment venu.
 */
@Composable
private fun BlockRow(block: WorkBlock) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(
            TIME.format(block.start) + " – " + TIME.format(block.end),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (block.notifies) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            "  " + block.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HoursBadge(blocks: List<WorkBlock>) {
    val total = blocks.fold(Duration.ZERO) { acc, block -> acc.plus(block.duration) }
    if (total.isZero) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            formatDuration(total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderChip(reminder: Reminder) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            shortLabel(reminder.kind) + " " + TIME.format(reminder.at),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String =
    when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Aujourd'hui · " + DAY.format(date)
        1L -> "Demain · " + DAY.format(date)
        else -> DAY.format(date)
    }

/**
 * Compte à rebours en jours de calendrier au-delà de vingt-quatre heures, et non en
 * tranches de 24 h : un rappel de lundi matin vu samedi après-midi est « dans 2 jours »,
 * là où l'arrondi vers le bas d'une durée annoncerait « dans 1 j ».
 */
private fun countdown(now: ZonedDateTime, target: ZonedDateTime): String {
    val minutes = ChronoUnit.MINUTES.between(now, target)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
    return when {
        minutes < 1 -> "maintenant"
        minutes < 60 -> "dans " + minutes + " min"
        days == 0L -> "dans " + formatDuration(Duration.ofMinutes(minutes))
        days == 1L -> "demain"
        else -> "dans " + days + " jours"
    }
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (minutes == 0L) hours.toString() + " h" else hours.toString() + " h " + minutes
}

/**
 * Vocabulaire unique dans toute l'application, aligné sur celui de Skello : entrée,
 * sortie. La forme courte est une troncature de la longue, jamais un autre mot — sans
 * quoi la même échéance porte quatre noms selon l'écran.
 */
private fun label(kind: ReminderKind): String = when (kind) {
    ReminderKind.CLOCK_IN -> "Entrée"
    ReminderKind.BREAK_OUT -> "Départ en pause"
    ReminderKind.BREAK_IN -> "Retour de pause"
    ReminderKind.SHIFT_CHANGE -> "Changement de poste"
    ReminderKind.CLOCK_OUT -> "Sortie"
}

private fun shortLabel(kind: ReminderKind): String = when (kind) {
    ReminderKind.CLOCK_IN -> "Entrée"
    ReminderKind.BREAK_OUT -> "Pause"
    ReminderKind.BREAK_IN -> "Retour"
    ReminderKind.SHIFT_CHANGE -> "Changement"
    ReminderKind.CLOCK_OUT -> "Sortie"
}
