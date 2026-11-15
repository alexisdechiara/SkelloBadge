package fr.gaddiction.skellobadge

import fr.gaddiction.skellobadge.domain.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `bullets and emphasis are flattened into plain text`() {
        val lines = ReleaseNotes.lines(
            """
            - **La pause lue dans la note** — « Pause cantine 12h-13h » est reconnue.
            - Mises à jour proposées *depuis l'application*.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ReleaseNotes.Line("La pause lue dans la note — « Pause cantine 12h-13h » est reconnue.", true),
                ReleaseNotes.Line("Mises à jour proposées depuis l'application.", true),
            ),
            lines,
        )
    }

    /** C'est la forme du CHANGELOG : des puces coupées à 90 colonnes. */
    @Test
    fun `a bullet wrapped over several lines stays one item`() {
        val lines = ReleaseNotes.lines(
            """
            - **La pause lue dans la note** — « Pause cantine 12h-13h »
              ou le trou entre deux animations datées.
            - Deuxième point.

            Paragraphe libre
            sur deux lignes.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ReleaseNotes.Line(
                    "La pause lue dans la note — « Pause cantine 12h-13h » ou le trou entre " +
                        "deux animations datées.",
                    true,
                ),
                ReleaseNotes.Line("Deuxième point.", true),
                ReleaseNotes.Line("Paragraphe libre sur deux lignes.", false),
            ),
            lines,
        )
    }

    @Test
    fun `headings and links keep only their text`() {
        val lines = ReleaseNotes.lines("### Corrections\nVoir [le guide](https://example.test/guide).")
        assertEquals(
            listOf(
                ReleaseNotes.Line("Corrections", false),
                ReleaseNotes.Line("Voir le guide.", false),
            ),
            lines,
        )
    }

    /** C'est tout ce que contenaient les notes générées par GitHub jusqu'ici. */
    @Test
    fun `github boilerplate is dropped`() {
        val generated = "**Full Changelog**: https://github.com/o/r/compare/v1.1.0...v1.1.1"
        assertTrue(ReleaseNotes.lines(generated).isEmpty())
        assertFalse(ReleaseNotes.hasContent(generated))
        assertFalse(ReleaseNotes.hasContent(""))
        assertFalse(ReleaseNotes.hasContent(null))
    }

    @Test
    fun `french punctuation and underscores in words survive`() {
        val lines = ReleaseNotes.lines("- L'heure « {heure} » s'affiche : 12h_30 reste tel quel")
        assertEquals("L'heure « {heure} » s'affiche : 12h_30 reste tel quel", lines.single().text)
    }
}
