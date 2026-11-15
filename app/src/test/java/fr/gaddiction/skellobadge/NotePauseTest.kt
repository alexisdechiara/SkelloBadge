package fr.gaddiction.skellobadge

import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.NotePause
import fr.gaddiction.skellobadge.domain.Pause
import fr.gaddiction.skellobadge.domain.PlanningConfig
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.domain.PlanningEvent
import fr.gaddiction.skellobadge.domain.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Lecture de l'heure de pause dans les notes libres du planning, telles que les
 * responsables les écrivent : « Pause cantine 12h-13h », « pause entre les deux anims »,
 * ou simplement les horaires de chaque animation.
 */
class NotePauseTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val date = LocalDate.parse("2026-09-15")

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = date): ZonedDateTime =
        day.atTime(hour, minute).atZone(zone)

    private fun find(
        note: String,
        from: ZonedDateTime = at(9),
        to: ZonedDateTime = at(17),
        keywords: Set<String> = NotePause.DEFAULT_KEYWORDS,
    ): Pause? = NotePause.find(from, to, note, keywords)

    private fun assertPause(pause: Pause?, start: LocalTime, end: LocalTime) {
        assertNotNull("aucune pause trouvée", pause)
        assertEquals(start, pause!!.start.toLocalTime())
        assertEquals(end, pause.end.toLocalTime())
    }

    @Test
    fun `a named lunch break is read with both bounds`() {
        val pause = find("Pause cantine 12h-13h")
        assertPause(pause, LocalTime.of(12, 0), LocalTime.of(13, 0))
        assertEquals(Pause.Origin.NOTE, pause!!.origin)
        assertEquals("Pause cantine 12h-13h", pause.excerpt)
    }

    @Test
    fun `minutes, arrows and colons are all understood`() {
        assertPause(find("Repas : 11h45 → 12h30"), LocalTime.of(11, 45), LocalTime.of(12, 30))
        assertPause(find("cantine 12:15 / 13:00"), LocalTime.of(12, 15), LocalTime.of(13, 0))
        assertPause(find("Pause de 12 h 30 à 13 h 15"), LocalTime.of(12, 30), LocalTime.of(13, 15))
    }

    @Test
    fun `case and accents do not matter`() {
        assertPause(find("PAUSE DÉJ 12H30-13H15"), LocalTime.of(12, 30), LocalTime.of(13, 15))
    }

    /** Une heure seule ouvre la pause ; la durée par défaut la ferme. */
    @Test
    fun `a start time alone gets the default length`() {
        assertPause(
            find("Pause entre les deux anims à 12h30"),
            LocalTime.of(12, 30),
            LocalTime.of(13, 30),
        )
    }

    @Test
    fun `a stated length wins over the default one`() {
        assertPause(find("Pause 45 min à 12h15"), LocalTime.of(12, 15), LocalTime.of(13, 0))
    }

    @Test
    fun `noon written in words is a time`() {
        assertPause(find("Pause cantine à midi"), LocalTime.NOON, LocalTime.of(13, 0))
    }

    /** Sur une même ligne, ce sont les heures qui suivent le mot qui désignent la pause. */
    @Test
    fun `times after the keyword are the ones that count`() {
        assertPause(
            find("Anim 9h-11h, pause 12h-13h, anim 14h-16h"),
            LocalTime.of(12, 0),
            LocalTime.of(13, 0),
        )
        assertPause(
            find("Atelier 9h30-11h puis pause 12h-12h45"),
            LocalTime.of(12, 0),
            LocalTime.of(12, 45),
        )
    }

    /** La note date les deux animations sans nommer la pause : c'est le trou entre elles. */
    @Test
    fun `the gap between two dated activities is the break`() {
        val pause = find("Anim 1 : 9h30-11h30\nAnim 2 : 13h30-16h")
        assertPause(pause, LocalTime.of(11, 30), LocalTime.of(13, 30))
        assertEquals(Pause.Origin.NOTE_GAP, pause!!.origin)
    }

    @Test
    fun `a keyword without a time falls back on the gap between activities`() {
        assertPause(
            find("Anim matin 9h-11h45\nPause entre les deux anims\nAnim aprem 13h15-16h30"),
            LocalTime.of(11, 45),
            LocalTime.of(13, 15),
        )
    }

    /** Un battement de vingt minutes entre deux ateliers du matin ne masque pas le midi. */
    @Test
    fun `the longest gap is the one kept`() {
        assertPause(
            find("Atelier 9h-10h\nAtelier 10h20-11h30\nAtelier 13h-16h"),
            LocalTime.of(11, 30),
            LocalTime.of(13, 0),
        )
    }

    @Test
    fun `a short gap is not a break`() {
        assertNull(find("Atelier 9h-12h\nAtelier 12h10-16h"))
    }

    /** Les heures hors du créneau — trajet, covoiturage — ne sont jamais prises pour une pause. */
    @Test
    fun `travel times outside the shift are ignored`() {
        assertNull(find("A confirmer avec Quentin\n\nSi EG :\nUniversite Lyon\nTrajet: 8h\nFin: 17h"))
        assertNull(find("Covoit: 7h20\nFin: 18h15\nChef: Alvyn"))
    }

    @Test
    fun `a break outside the shift is refused`() {
        assertNull(find("Pause 18h-19h"))
        assertNull(find("Pause 8h-9h30"))
    }

    @Test
    fun `an implausibly long break is refused`() {
        assertNull(find("Pause 9h30-16h"))
    }

    @Test
    fun `a duration is read as a length, not as a time`() {
        assertPause(find("Pause 1h30 à midi"), LocalTime.NOON, LocalTime.of(13, 30))
    }

    @Test
    fun `phone numbers and numbered activities are not times`() {
        assertPause(
            find("Tel 06 12 34 56 78, pause 12h"),
            LocalTime.NOON,
            LocalTime.of(13, 0),
        )
    }

    /** « dej » ne doit pas surgir dans « déjà », ni « pause » rester muet au pluriel. */
    @Test
    fun `keywords are matched on whole words`() {
        assertNull(find("Déjà vu 12h-13h"))
        assertPause(find("Pauses 12h-13h"), LocalTime.of(12, 0), LocalTime.of(13, 0))
    }

    @Test
    fun `an empty or silent note gives nothing`() {
        assertNull(find(""))
        assertNull(find("Binôme sur place"))
    }

    @Test
    fun `custom keywords are honoured`() {
        assertPause(
            find("Goûter 16h-16h30", from = at(13), to = at(18), keywords = setOf("goûter")),
            LocalTime.of(16, 0),
            LocalTime.of(16, 30),
        )
    }

    @Test
    fun `an evening shift crossing midnight places the break on the next day`() {
        val pause = find(
            "Pause 23h30-0h",
            from = at(20),
            to = at(2, day = date.plusDays(1)),
        )
        assertNotNull(pause)
        assertEquals(at(23, 30), pause!!.start)
        assertEquals(at(0, day = date.plusDays(1)), pause.end)
    }

    // --- Intégration au moteur -------------------------------------------------------

    private fun event(
        title: String,
        from: ZonedDateTime,
        to: ZonedDateTime,
        note: String? = null,
    ) = PlanningEvent(uid = title + from, title = title, note = note, start = from, end = to)

    private fun dayOf(events: List<PlanningEvent>, config: PlanningConfig = PlanningConfig()) =
        PlanningEngine.build(events, config).first { it.date == date } as DayPlan.Work

    private fun times(day: DayPlan.Work, kind: ReminderKind) =
        day.reminders.filter { it.kind == kind }.map { it.at.toLocalTime() }

    @Test
    fun `the note replaces the fixed midday break`() {
        val day = dayOf(listOf(event("Centre de loisirs", at(9), at(17), "Pause cantine 12h30-13h30")))

        assertEquals(
            listOf(
                ReminderKind.CLOCK_IN,
                ReminderKind.BREAK_OUT,
                ReminderKind.BREAK_IN,
                ReminderKind.CLOCK_OUT,
            ),
            day.reminders.map { it.kind },
        )
        assertEquals(listOf(LocalTime.of(12, 30)), times(day, ReminderKind.BREAK_OUT))
        assertEquals(listOf(LocalTime.of(13, 29)), times(day, ReminderKind.BREAK_IN))
        assertEquals(Pause.Origin.NOTE, day.blocks.single().pause?.origin)

        // La notification dit d'où vient l'heure.
        val out = day.reminders.first { it.kind == ReminderKind.BREAK_OUT }
        assertTrue(out.note!!.contains("Pause cantine 12h30-13h30"))
    }

    @Test
    fun `without a usable note the fixed break still applies and says so`() {
        val day = dayOf(listOf(event("Centre de loisirs", at(9), at(17), "Binôme sur place")))
        assertEquals(listOf(LocalTime.NOON), times(day, ReminderKind.BREAK_OUT))
        assertEquals(Pause.Origin.DEFAULT, day.blocks.single().pause?.origin)
    }

    @Test
    fun `turning note reading off restores the fixed break`() {
        val day = dayOf(
            listOf(event("Centre de loisirs", at(9), at(17), "Pause cantine 12h30-13h30")),
            PlanningConfig(notePauseEnabled = false),
        )
        assertEquals(listOf(LocalTime.NOON), times(day, ReminderKind.BREAK_OUT))
    }

    @Test
    fun `the note break applies even when the fixed break is off`() {
        val day = dayOf(
            listOf(event("Centre de loisirs", at(9), at(17), "Pause cantine 12h30-13h30")),
            PlanningConfig(lunchFallbackEnabled = false),
        )
        assertEquals(listOf(LocalTime.of(12, 30)), times(day, ReminderKind.BREAK_OUT))
    }

    /**
     * Sur une journée déjà coupée au planning, le repli fixe se tait comme avant, mais une
     * pause écrite dans la note d'un des créneaux reste une vraie pause à badger.
     */
    @Test
    fun `a note break inside one shift of a split day is added`() {
        val day = dayOf(
            listOf(
                event("Accueil", at(8), at(14), "Pause 11h-11h30"),
                event("Périscolaire", at(16), at(19)),
            ),
        )
        assertEquals(
            listOf(LocalTime.of(11, 0), LocalTime.of(14, 0)),
            times(day, ReminderKind.BREAK_OUT),
        )
        assertEquals(
            listOf(LocalTime.of(11, 29), LocalTime.of(15, 59)),
            times(day, ReminderKind.BREAK_IN),
        )
        assertNull(day.blocks.last().pause)
    }

    @Test
    fun `a standby shift gets no break either`() {
        val day = dayOf(listOf(event("EG ou Off", at(9), at(17), "Pause cantine 12h-13h")))
        assertNull(day.blocks.single().pause)
        assertTrue(day.reminders.none { it.kind == ReminderKind.BREAK_OUT })
    }
}
