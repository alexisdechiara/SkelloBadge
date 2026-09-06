package fr.gaddiction.skellobadge

import fr.gaddiction.skellobadge.data.ics.IcsParser
import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.PlanningConfig
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.domain.PlanningEvent
import fr.gaddiction.skellobadge.domain.Reminder
import fr.gaddiction.skellobadge.domain.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Le jeu d'essai reproduit les cas réellement présents dans un flux Skello : jour de
 * repos codé de minuit à minuit, journée coupée, deux services enchaînés sans écart,
 * journée continue, service de soirée, créneau de réserve « ou off », et un jour de
 * changement d'heure de 25 h.
 */
class PlanningEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val config = PlanningConfig()

    private fun rawFixture(): String =
        checkNotNull(javaClass.classLoader)
            .getResourceAsStream("planning-sample.ics")
            .let(::checkNotNull)
            .bufferedReader()
            .readText()

    private fun events(fallbackZone: ZoneId = zone): List<PlanningEvent> =
        IcsParser.parse(rawFixture(), fallbackZone).events

    private fun days(cfg: PlanningConfig = config) = PlanningEngine.build(events(), cfg)

    private fun day(date: String, cfg: PlanningConfig = config): DayPlan =
        days(cfg).first { it.date == LocalDate.parse(date) }

    private fun work(date: String, cfg: PlanningConfig = config): DayPlan.Work =
        day(date, cfg) as DayPlan.Work

    /**
     * Rappels de badgeage seuls : une journée peut porter en plus la demande de
     * confirmation du lendemain, qui n'a rien à voir avec ses propres échéances.
     */
    private fun remindersOf(date: String, cfg: PlanningConfig = config): List<Reminder> =
        work(date, cfg).reminders.filterNot { it.kind == ReminderKind.STANDBY_CONFIRM }

    private fun at(reminder: Reminder): LocalTime = reminder.at.toLocalTime()

    @Test
    fun `parses every event and strips the Skello prefix`() {
        val parsed = events()
        assertEquals(19, parsed.size)
        assertTrue(parsed.none { it.title.startsWith("Shift:") })
    }

    private fun confirmationsOn(date: String, cfg: PlanningConfig = config): List<Reminder> =
        day(date, cfg).reminders.filter { it.kind == ReminderKind.STANDBY_CONFIRM }

    private fun allConfirmations(cfg: PlanningConfig = config): List<Reminder> =
        days(cfg).flatMap { it.reminders }.filter { it.kind == ReminderKind.STANDBY_CONFIRM }

    /**
     * Un service dont le libellé annonce une alternative n'est fixé que la veille : il
     * faut donc penser à poser la question. Le planning porte lui-même la consigne.
     */
    @Test
    fun `a day to confirm is asked about the day before`() {
        val reminders = confirmationsOn("2026-09-05")
        assertEquals(1, reminders.size)
        assertEquals("EG ou Off", reminders.single().title)
    }

    /** Veille en repos : on est joignable plus tôt dans l'après-midi. */
    @Test
    fun `the question falls at the rest time when the eve is free`() {
        assertTrue(day("2026-09-05") is DayPlan.Off)
        assertEquals(LocalTime.of(14, 0), at(confirmationsOn("2026-09-05").single()))
    }

    /** Veille travaillée : la question attend la fin de la journée. */
    @Test
    fun `the question falls at the working time when the eve is worked`() {
        assertTrue(work("2026-08-27").blocks.single().notifies)
        assertEquals(LocalTime.of(17, 0), at(confirmationsOn("2026-08-27").single()))
    }

    /**
     * « EG ou Bureau » se travaille dans les deux cas : la journée garde ses rappels de
     * badgeage tout en donnant lieu à une demande la veille.
     */
    @Test
    fun `a day to confirm that is worked either way keeps its badging reminders`() {
        val plan = work("2026-08-28")
        assertTrue(plan.blocks.single().notifies)
        // Journée continue de 09h à 17h : le repli de pause s'applique comme ailleurs.
        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            remindersOf("2026-08-28").map { it.kind },
        )
        assertEquals(1, confirmationsOn("2026-08-27").size)
    }

    /** La veille est ici un repos hebdomadaire : elle doit tout de même porter le rappel. */
    @Test
    fun `the question lands on a rest day when that is the eve`() {
        assertTrue(day("2026-09-05") is DayPlan.Off)
        assertEquals(1, confirmationsOn("2026-09-05").size)
    }

    /**
     * Deux réserves qui se suivent relèvent du même remplacement quand leur description
     * est identique : une seule demande, la veille du premier jour.
     */
    @Test
    fun `consecutive standby days sharing a description are asked about once`() {
        assertEquals(1, confirmationsOn("2026-09-16").size)
        assertTrue(confirmationsOn("2026-09-17").isEmpty())
    }

    /** Descriptions différentes : deux remplacements distincts, donc deux demandes. */
    @Test
    fun `consecutive standby days with different descriptions are asked about separately`() {
        assertEquals(1, confirmationsOn("2026-09-23").size)
        assertEquals(1, confirmationsOn("2026-09-24").size)
    }

    /**
     * Deux journées au même endroit relèvent du même déplacement, même si tout le reste
     * de la note diffère : horaires, covoiturage, encadrant. Une seule question.
     */
    @Test
    fun `two days at the same venue are asked about once`() {
        assertEquals(1, confirmationsOn("2026-10-04").size)
        assertTrue(confirmationsOn("2026-10-05").isEmpty())
    }

    /** Le lieu change : c'est un autre déplacement, qui appelle sa propre question. */
    @Test
    fun `a change of venue opens a new question`() {
        assertEquals(1, confirmationsOn("2026-10-06").size)
    }

    /**
     * Le service ordinaire qui partage la journée — ici une réunion de bureau — porte sa
     * propre note, sans rapport avec la question posée. La laisser entrer dans la
     * comparaison faisait paraître chaque jour distinct.
     */
    @Test
    fun `an ordinary shift sharing the day does not break the run`() {
        val plan = work("2026-10-05")
        assertEquals(2, plan.blocks.size)
        assertTrue(plan.blocks.any { !it.title.contains(" ou ") })
        assertTrue(confirmationsOn("2026-10-05").isEmpty())
    }

    @Test
    fun `the sample yields exactly one question per run`() {
        // 28/08, 06/09, 07/09, 10/09, 17-18/09 groupés, 24/09, 25/09,
        // 05-06/10 groupés (même lieu), 07/10 (lieu différent).
        assertEquals(9, allConfirmations().size)
    }

    /** Une fois la journée déclarée travaillée, la question ne se pose plus. */
    @Test
    fun `declaring a standby day as worked removes its question`() {
        val worked = config.copy(workingDates = setOf(LocalDate.parse("2026-09-06")))
        assertTrue(confirmationsOn("2026-09-05", worked).isEmpty())
    }

    @Test
    fun `the question disappears entirely when disabled`() {
        assertTrue(allConfirmations(config.copy(standbyAskEnabled = false)).isEmpty())
    }

    /** Une démarche, pas un badgeage : elle ne doit pas s'ajouter aux rappels de la journée. */
    @Test
    fun `a standby day still carries no badging reminder of its own`() {
        assertTrue(remindersOf("2026-09-06").isEmpty())
    }

    @Test
    fun `a weekly rest is a non working day`() {
        val plan = day("2026-09-05")
        assertTrue(plan is DayPlan.Off)
        assertEquals("Repos hebdomadaire", (plan as DayPlan.Off).label)
    }

    @Test
    fun `an absence marker is also a non working day`() {
        assertTrue(day("2026-08-24") is DayPlan.Off)
    }

    /**
     * Le 25 octobre 2026 dure 25 h à cause du passage à l'heure d'hiver. Un seuil fixé à
     * 24 h laisserait donc passer ce jour de repos.
     */
    @Test
    fun `a day off spanning a daylight saving change is still detected`() {
        assertTrue(day("2026-10-25") is DayPlan.Off)
    }

    @Test
    fun `every non working day of the sample is recognised`() {
        assertEquals(3, days().count { it is DayPlan.Off })
    }

    @Test
    fun `a split day yields four reminders around a real break`() {
        val reminders = remindersOf("2026-09-07")
        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(11, 29), at(reminders[0]))
        assertEquals(LocalTime.of(12, 15), at(reminders[1]))
        assertEquals(LocalTime.of(12, 59), at(reminders[2]))
        assertEquals(LocalTime.of(17, 0), at(reminders[3]))
    }

    /**
     * Écart minimal à zéro, le réglage par défaut : deux services collés se badgent
     * quand même séparément, sortie puis entrée. Le préavis d'entrée est ramené à
     * l'heure de la sortie pour que les deux gestes restent dans l'ordre.
     */
    @Test
    fun `back to back shifts yield an exit then an entry`() {
        val reminders = remindersOf("2026-09-10")
        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(9, 59), at(reminders[0]))
        assertEquals(LocalTime.of(19, 0), at(reminders[1]))
        assertEquals(LocalTime.of(19, 0), at(reminders[2]))
        assertEquals(LocalTime.of(21, 0), at(reminders[3]))
        assertEquals("EG ou Bureau", reminders[1].title)
        assertEquals("Equipe Mobile", reminders[2].title)
    }

    /**
     * Au-delà de zéro, l'écart minimal retrouve son rôle de seuil : un enchaînement sans
     * coupure passe sous la barre et ne donne plus qu'un rappel, groupé.
     */
    @Test
    fun `a threshold turns back to back shifts back into a single reminder`() {
        val reminders = remindersOf(
            "2026-09-10",
            config.copy(breakMinGap = Duration.ofMinutes(5)),
        )
        assertEquals(
            listOf(ReminderKind.CLOCK_IN, ReminderKind.SHIFT_CHANGE, ReminderKind.CLOCK_OUT),
            reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(19, 0), at(reminders[1]))
        assertEquals("EG ou Bureau → Equipe Mobile", reminders[1].title)
    }

    /** Un seuil ne doit pas avaler une vraie coupure : 45 min restent une pause. */
    @Test
    fun `a threshold leaves a real break untouched`() {
        val reminders = remindersOf(
            "2026-09-07",
            config.copy(breakMinGap = Duration.ofMinutes(5)),
        )
        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            reminders.map { it.kind },
        )
    }

    @Test
    fun `the two shifts of a chained day are never merged`() {
        assertEquals(2, work("2026-09-10").blocks.size)
    }

    @Test
    fun `an evening shift is handled like any other`() {
        val reminders = remindersOf("2026-09-04")
        assertEquals(LocalTime.of(17, 59), at(reminders[0]))
        assertEquals(LocalTime.of(20, 0), at(reminders[1]))
    }

    /** Vérifie à la fois le dépliage des lignes et le déséchappement du texte. */
    @Test
    fun `a folded and escaped note is recovered whole`() {
        val note = work("2026-08-27").blocks.single().note
        assertNotNull(note)
        assertTrue(note!!.contains("Rdv à 9h30 sur place"))
        assertTrue(note.contains("Prévoir le matériel"))
        assertTrue(note.contains("\n"))
    }

    @Test
    fun `the midday fallback is on by default and covers a continuous day`() {
        val reminders = remindersOf("2026-08-27")
        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(12, 0), at(reminders[1]))
        assertEquals(LocalTime.of(12, 59), at(reminders[2]))
    }

    @Test
    fun `disabling the midday fallback leaves only the day boundaries`() {
        val reminders = remindersOf("2026-08-27", config.copy(lunchFallbackEnabled = false))
        assertEquals(
            listOf(ReminderKind.CLOCK_IN, ReminderKind.CLOCK_OUT),
            reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(9, 34), at(reminders[0]))
        assertEquals(LocalTime.of(19, 5), at(reminders[1]))
    }

    /**
     * « EG ou Off » désigne une journée de réserve : la présence n'est requise qu'en
     * renfort. Le créneau reste visible au planning avec ses horaires, mais ne sonne pas.
     */
    @Test
    fun `a standby shift stays visible but never rings`() {
        val plan = work("2026-09-06")
        assertEquals(1, plan.blocks.size)
        assertEquals("EG ou Off", plan.blocks.single().title)
        assertFalse(plan.blocks.single().notifies)
        assertTrue(remindersOf("2026-09-06").isEmpty())
    }

    /**
     * Le jour où l'on est effectivement appelé en renfort est celui où un oubli coûte le
     * plus cher. Déclarer la journée travaillée doit donc rétablir tous ses rappels.
     */
    @Test
    fun `declaring a standby day as worked restores its reminders`() {
        val date = LocalDate.parse("2026-09-06")
        val cfg = config.copy(workingDates = setOf(date))
        val plan = work("2026-09-06", cfg)

        assertTrue(plan.blocks.single().notifies)
        assertEquals(
            listOf(ReminderKind.CLOCK_IN, ReminderKind.CLOCK_OUT),
            remindersOf("2026-09-06", cfg).map { it.kind },
        )
        assertEquals(LocalTime.of(12, 59), at(remindersOf("2026-09-06", cfg).first()))
        assertEquals(LocalTime.of(19, 0), at(remindersOf("2026-09-06", cfg).last()))
    }

    /** La déclaration l'emporte aussi sur une mise en sourdine par type. */
    @Test
    fun `declaring a day as worked overrides a muted shift type`() {
        val date = LocalDate.parse("2026-09-04")
        val muted = config.copy(
            disabledTypes = setOf("Equipe Mobile"),
            workingDates = setOf(date),
        )
        val plan = work("2026-09-04", muted)

        assertTrue(plan.blocks.single().notifies)
        assertEquals(2, plan.reminders.size)
    }

    @Test
    fun `a day not declared as worked stays silent`() {
        val other = config.copy(workingDates = setOf(LocalDate.parse("2026-09-14")))
        assertTrue(remindersOf("2026-09-06", other).isEmpty())
    }

    @Test
    fun `clearing the standby patterns makes the joker shift ring again`() {
        val cfg = config.copy(standbyPatterns = emptySet())
        assertTrue(work("2026-09-06", cfg).blocks.single().notifies)
        assertEquals(2, remindersOf("2026-09-06", cfg).size)
    }

    @Test
    fun `a shift type put on mute produces no reminder`() {
        val muted = config.copy(disabledTypes = setOf("Equipe Mobile"))
        val evening = work("2026-09-04", muted)
        assertFalse(evening.blocks.single().notifies)
        assertTrue(evening.reminders.isEmpty())
    }

    /**
     * Sur une journée mixte, seul le créneau encore actif compte : les bornes se
     * recalculent dessus au lieu de laisser un rappel orphelin sur le créneau muet.
     */
    @Test
    fun `muting one shift of a chained day recomputes the remaining boundaries`() {
        // Repli de midi neutralisé pour n'observer que l'effet de la mise en sourdine.
        val muted = config.copy(
            disabledTypes = setOf("Equipe Mobile"),
            lunchFallbackEnabled = false,
        )
        val plan = work("2026-09-10", muted)

        // Les deux créneaux restent affichés, mais la bascule de 19h disparaît : la fin de
        // journée est désormais la fin du seul créneau encore actif.
        assertEquals(2, plan.blocks.size)
        assertEquals(
            listOf(ReminderKind.CLOCK_IN, ReminderKind.CLOCK_OUT),
            plan.reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(9, 59), at(plan.reminders[0]))
        assertEquals(LocalTime.of(19, 0), at(plan.reminders[1]))
    }

    /**
     * Sur la même journée, le repli de midi s'applique bien au créneau restant : c'est le
     * comportement voulu, mais il vaut la peine d'être fixé explicitement.
     */
    @Test
    fun `the midday fallback still applies to what remains after muting`() {
        val muted = config.copy(disabledTypes = setOf("Equipe Mobile"))
        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            remindersOf("2026-09-10", muted).map { it.kind },
        )
    }

    /**
     * Le fuseau de l'appareil ne doit avoir aucune influence sur l'interprétation.
     *
     * Régression constatée sur un émulateur réglé en UTC : la détection convertissait
     * d'abord l'événement dans le fuseau du téléphone, si bien qu'un repos de 00h00
     * Europe/Paris se lisait 22h00 la veille et redevenait une journée travaillée. Les
     * jours non travaillés du planning avaient purement disparu.
     */
    @Test
    fun `the interpretation does not depend on the device time zone`() {
        val fromUtc = PlanningEngine.build(events(ZoneId.of("UTC")), config)

        assertEquals(3, fromUtc.count { it is DayPlan.Off })
        assertTrue(fromUtc.first { it.date == LocalDate.parse("2026-09-05") } is DayPlan.Off)

        val split = fromUtc.first { it.date == LocalDate.parse("2026-09-07") } as DayPlan.Work
        assertEquals(LocalTime.of(11, 29), split.reminders.first().at.toLocalTime())
        assertEquals(LocalTime.of(17, 0), split.reminders.last().at.toLocalTime())
    }

    /** Une même minute ne peut pas porter deux rappels de même nature. */
    @Test
    fun `reminder identifiers are unique across the whole horizon`() {
        val ids = days().filterIsInstance<DayPlan.Work>().flatMap { it.reminders }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
