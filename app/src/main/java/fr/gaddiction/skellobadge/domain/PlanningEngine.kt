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
    fun build(events: List<PlanningEvent>, config: PlanningConfig): List<DayPlan> {
        val days = events
            .groupBy { it.start.toLocalDate() }
            .entries
            .sortedBy { it.key }
            .map { (date, dayEvents) -> buildDay(date, dayEvents, config) }

        return withStandbyConfirmations(days, config)
    }

    /**
     * Ajoute, la veille de chaque série de réserve, un rappel de demander au responsable
     * si l'on remplace quelqu'un.
     *
     * Une série, et non chaque journée : deux jours de réserve qui se suivent relèvent le
     * plus souvent du même remplacement, auquel cas une seule demande suffit. On les
     * distingue par leur description — c'est le seul élément du planning qui dise de quel
     * évènement il s'agit, deux réserves consécutives portant le même libellé.
     */
    private fun withStandbyConfirmations(
        days: List<DayPlan>,
        config: PlanningConfig,
    ): List<DayPlan> {
        if (!config.standbyAskEnabled) return days

        val byDate = days.associateByTo(LinkedHashMap()) { it.date }

        days.filter { needsConfirmation(it, config) }
            .filter { day ->
                // Premier jour de sa série : la veille n'est pas à confirmer, ou l'est
                // pour un autre évènement.
                val previous = byDate[day.date.minusDays(1)]
                previous == null ||
                    !needsConfirmation(previous, config) ||
                    signature(previous, config) != signature(day, config)
            }
            .forEach { day ->
                val eve = day.date.minusDays(1)
                val block = (day as DayPlan.Work).blocks
                    .first { config.isAlternativeTitle(it.title) }

                // L'heure suit la disponibilité de la veille : en fin de journée si l'on
                // travaille, plus tôt si l'on est en repos.
                val time = if (isWorked(byDate[eve])) {
                    config.askTimeWorking
                } else {
                    config.askTimeRest
                }
                val at = eve.atTime(time).atZone(block.start.zone)

                val reminder = Reminder(
                    at = at,
                    actionAt = at,
                    kind = ReminderKind.STANDBY_CONFIRM,
                    title = block.title,
                    note = day.blocks.firstNotNullOfOrNull { it.note },
                )

                byDate[eve] = when (val existing = byDate[eve]) {
                    null -> DayPlan.Empty(eve, listOf(reminder))
                    is DayPlan.Off -> existing.copy(reminders = existing.reminders + reminder)
                    is DayPlan.Empty -> existing.copy(reminders = existing.reminders + reminder)
                    is DayPlan.Work -> existing.copy(reminders = existing.reminders + reminder)
                }
            }

        return byDate.values.sortedBy { it.date }
    }

    /**
     * Journée à confirmer : au moins un créneau annonce une alternative.
     *
     * « EG ou Bureau » comme « EG ou Off » ne sont fixés que la veille, et méritent donc
     * la même question — même si le premier se travaille dans les deux cas. Déclarer la
     * journée travaillée y répond par avance et fait disparaître le rappel.
     */
    private fun needsConfirmation(day: DayPlan, config: PlanningConfig): Boolean {
        if (day !is DayPlan.Work) return false
        if (day.date in config.workingDates) return false
        return day.blocks.any { config.isAlternativeTitle(it.title) }
    }

    /** La veille est-elle travaillée ? Détermine l'heure à laquelle on est joignable. */
    private fun isWorked(day: DayPlan?): Boolean =
        day is DayPlan.Work && day.blocks.any { it.notifies }

    /**
     * Empreinte de l'évènement à confirmer, servant à reconnaître deux journées qui n'en
     * font qu'une.
     *
     * Deux précautions, apprises du planning réel. D'abord, seuls les créneaux alternatifs
     * comptent : une journée peut porter en plus un service ordinaire — « Académie de
     * l'engagement », « Equipe Mobile » — dont la note n'a rien à voir avec la question
     * posée, et ferait croire à un évènement différent chaque jour.
     *
     * Ensuite, c'est le lieu qui fait foi. Le reste de la note varie nécessairement d'un
     * jour à l'autre — horaires, trajet, repas, équipe — alors que le lieu ne change qu'au
     * passage d'un déplacement au suivant. Deux journées au même endroit relèvent du même
     * évènement ; un changement de lieu en ouvre un nouveau, qui appelle sa propre question.
     */
    private fun signature(day: DayPlan, config: PlanningConfig): String =
        (day as? DayPlan.Work)
            ?.blocks
            ?.filter { config.isAlternativeTitle(it.title) }
            ?.joinToString(separator = "|") { block ->
                normalize(block.title) + "#" + normalize(venue(block.note))
            }
            .orEmpty()

    /**
     * Lieu de l'évènement, tel que la note le désigne.
     *
     * Ces notes s'ouvrent sur un en-tête décrivant l'arrangement, puis, après une ligne
     * vide, énumèrent le détail du jour en commençant par le lieu. Un marqueur seul —
     * « Si EG : » — le précède parfois sur sa propre ligne, et se reconnaît à son
     * deux-points final. Faute de détail, l'en-tête sert de référence.
     */
    private fun venue(note: String?): String {
        val lines = note.orEmpty().lines().map(String::trim)
        val headerEnd = lines.indexOfFirst(String::isEmpty).takeIf { it >= 0 } ?: lines.size
        val header = lines.take(headerEnd).joinToString(separator = " ")
        val details = lines.drop(headerEnd).filter(String::isNotEmpty)
        return details.firstOrNull { !it.endsWith(":") } ?: header
    }

    /** Insensible à la casse et aux espaces : Skello reformate ses notes sans les changer. */
    private fun normalize(text: String): String =
        text.lowercase().filterNot(Char::isWhitespace)

    /** Tous les rappels encore à venir, dans l'ordre chronologique. */
    fun upcomingReminders(
        days: List<DayPlan>,
        now: ZonedDateTime,
        horizon: Duration = Duration.ofDays(14),
    ): List<Reminder> {
        val limit = now.plus(horizon)
        return days
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

        // Journée déclarée travaillée : tout sonne, y compris ce que les règles de sourdine
        // auraient tu. C'est un choix explicite de l'utilisateur pour cette date précise.
        val forced = date in config.workingDates

        val blocks = dayEvents
            .filter { it.duration > Duration.ZERO }
            .sortedWith(compareBy({ it.start }, { it.end }))
            .map {
                WorkBlock(
                    start = it.start,
                    end = it.end,
                    title = it.title,
                    note = it.note,
                    notifies = forced || config.notifiesFor(it.title),
                )
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
            if (gap >= config.breakMinGap) {
                // Deux pointages distincts : on sort d'un créneau, on entre dans le suivant.
                val outAt = current.end.minus(config.breakOutLead)
                reminders += Reminder(
                    at = outAt,
                    actionAt = current.end,
                    kind = ReminderKind.BREAK_OUT,
                    title = current.title,
                    note = null,
                )
                // Le préavis de retour ne doit jamais précéder la sortie qu'il suit : sur
                // deux services collés, prévenir de la reprise avant d'avoir dit de sortir
                // inverserait les deux gestes à l'écran.
                reminders += Reminder(
                    at = maxOf(next.start.minus(config.breakInLead), outAt),
                    actionAt = next.start,
                    kind = ReminderKind.BREAK_IN,
                    title = next.title,
                    note = next.note,
                )
            } else {
                // Écart trop court pour une pause : un seul rappel, groupé.
                reminders += Reminder(
                    at = current.end,
                    actionAt = current.end,
                    kind = ReminderKind.SHIFT_CHANGE,
                    title = "${current.title} → ${next.title}",
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
