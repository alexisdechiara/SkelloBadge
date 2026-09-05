package fr.gaddiction.skellobadge.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Transforme une liste de créneaux bruts en journées interprétées, puis en rappels.
 *
 * Entièrement pur et sans dépendance Android : c'est ce qui permet de le tester
 * directement sur un vrai export de planning (voir PlanningEngineTest).
 */
object PlanningEngine {

    /**
     * Le regroupement par date se fait dans le fuseau porté par l'événement lui-même,
     * jamais dans celui du téléphone : une journée de planning est définie par
     * l'établissement, et doit rester la même que l'utilisateur soit à Nice ou en
     * déplacement à l'étranger.
     */
    fun build(events: List<PlanningEvent>, config: PlanningConfig): List<DayPlan> =
        events
            .groupBy { it.start.toLocalDate() }
            .entries
            .sortedBy { it.key }
            .map { (date, dayEvents) -> buildDay(date, dayEvents, config) }

    /** Tous les rappels encore à venir, dans l'ordre chronologique. */
    fun upcomingReminders(
        days: List<DayPlan>,
        now: ZonedDateTime,
        horizon: Duration = Duration.ofDays(14),
    ): List<Reminder> {
        val limit = now.plus(horizon)
        return days
            .filterIsInstance<DayPlan.Work>()
            .flatMap { it.reminders }
            .filter { it.at.isAfter(now) && it.at.isBefore(limit) }
            .sortedBy { it.at }
    }

    private fun buildDay(
        date: LocalDate,
        dayEvents: List<PlanningEvent>,
        config: PlanningConfig,
    ): DayPlan {
        // Un marqueur de jour non travaillé écrase tout le reste de la journée.
        dayEvents.firstOrNull { isNonWorkingMarker(it, config) }
            ?.let { return DayPlan.Off(date, it.title) }

        val blocks = dayEvents
            .filter { it.duration > Duration.ZERO }
            .sortedWith(compareBy({ it.start }, { it.end }))
            .map {
                WorkBlock(it.start, it.end, it.title, it.note, config.notifiesFor(it.title))
            }

        if (blocks.isEmpty()) return DayPlan.Empty(date)

        // Les créneaux de réserve et les types mis en sourdine restent affichés avec leurs
        // horaires, mais sont retirés du calcul : les bornes se recalculent sur ce qui reste,
        // si bien qu'une demi-journée en sourdine ne laisse pas un rappel orphelin.
        val notifying = blocks.filter(WorkBlock::notifies)
        val reminders = if (notifying.isEmpty()) emptyList() else remindersFor(notifying, config)
        return DayPlan.Work(date, blocks, reminders)
    }

    /**
     * Skello n'utilise pas d'événements « journée entière » au sens ICS : un repos ou une
     * absence est un créneau qui va de minuit à minuit. On reconnaît donc un jour non
     * travaillé à sa forme (départ à minuit local + durée d'au moins 23 h, ce qui absorbe
     * les journées de changement d'heure) plutôt qu'à son libellé — ainsi les congés,
     * arrêts maladie et jours fériés futurs sont couverts sans liste de mots-clés à tenir.
     *
     * Le test porte sur l'heure locale de l'événement dans son propre fuseau. Le convertir
     * d'abord dans celui du téléphone ferait échouer la détection dès que les deux
     * diffèrent : un repos à 00h00 Europe/Paris se lit 22h00 en UTC, et redeviendrait
     * une journée travaillée.
     */
    fun isNonWorkingMarker(event: PlanningEvent, config: PlanningConfig): Boolean {
        if (event.allDayFlag) return true
        return event.start.toLocalTime() == LocalTime.MIDNIGHT &&
            event.duration >= config.offDayMinDuration
    }

    private fun remindersFor(blocks: List<WorkBlock>, config: PlanningConfig): List<Reminder> {
        val reminders = mutableListOf<Reminder>()

        val first = blocks.first()
        reminders += Reminder(
            at = first.start.minus(config.clockInLead),
            actionAt = first.start,
            kind = ReminderKind.CLOCK_IN,
            title = first.title,
            note = first.note,
        )

        blocks.zipWithNext { current, next ->
            val gap = Duration.between(current.end, next.start)
            if (gap <= config.shiftChangeMaxGap) {
                // Postes enchaînés : on badge la sortie puis l'entrée dans la foulée.
                reminders += Reminder(
                    at = current.end,
                    actionAt = current.end,
                    kind = ReminderKind.SHIFT_CHANGE,
                    title = "${current.title} → ${next.title}",
                    note = next.note,
                )
            } else {
                reminders += Reminder(
                    at = current.end.minus(config.breakOutLead),
                    actionAt = current.end,
                    kind = ReminderKind.BREAK_OUT,
                    title = current.title,
                    note = null,
                )
                reminders += Reminder(
                    at = next.start.minus(config.breakInLead),
                    actionAt = next.start,
                    kind = ReminderKind.BREAK_IN,
                    title = next.title,
                    note = next.note,
                )
            }
        }

        val last = blocks.last()
        reminders += Reminder(
            at = last.end.minus(config.clockOutLead),
            actionAt = last.end,
            kind = ReminderKind.CLOCK_OUT,
            title = last.title,
            note = null,
        )

        if (config.lunchFallbackEnabled && blocks.size == 1) {
            reminders += lunchFallback(blocks.single(), config)
        }

        return reminders.sortedBy { it.at }
    }

    /**
     * Repli pour les journées d'un seul tenant qui enjambent midi sans coupure au planning.
     * Désactivé par défaut : sur un planning Skello réel, la majorité des journées longues
     * sont continues et ce repli produirait surtout des notifications inutiles.
     */
    private fun lunchFallback(block: WorkBlock, config: PlanningConfig): List<Reminder> {
        if (block.duration < config.lunchFallbackMinBlock) return emptyList()
        val zone = block.start.zone
        val date = block.start.toLocalDate()
        val out = date.atTime(config.lunchFallbackOut).atZone(zone)
        val back = date.atTime(config.lunchFallbackIn).atZone(zone)
        if (!block.start.isBefore(out) || !block.end.isAfter(back)) return emptyList()

        return listOf(
            Reminder(out, ReminderKind.BREAK_OUT, block.title, null, out),
            Reminder(back.minus(config.breakInLead), ReminderKind.BREAK_IN, block.title, null, back),
        )
    }
}
