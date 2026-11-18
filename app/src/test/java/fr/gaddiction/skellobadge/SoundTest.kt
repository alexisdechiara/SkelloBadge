package fr.gaddiction.skellobadge

import fr.gaddiction.skellobadge.data.Sound
import fr.gaddiction.skellobadge.data.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le son choisi est enregistré sous forme de texte. Une relecture ratée ferait retomber
 * silencieusement sur le son par défaut, sans que rien ne le signale.
 */
class SoundTest {

    @Test
    fun `every sound survives a round trip through its key`() {
        val sounds = Tone.entries.map { Sound.Bundled(it) } + Sound.Phone +
            Sound.Picked("content://media/internal/audio/media/42?title=Ding&canonical=1")
        sounds.forEach { assertEquals(it, Sound.parse(it.key)) }
    }

    @Test
    fun `an unknown or empty key is rejected`() {
        assertNull(Sound.parse(null))
        assertNull(Sound.parse(""))
        assertNull(Sound.parse("tone:DISPARU"))
        assertNull(Sound.parse("uri:"))
    }

    @Test
    fun `each tone points to its bundled file`() {
        assertEquals("tone_carillon", Tone.CARILLON.resourceName)
        assertEquals("tone_reveil", Tone.REVEIL.resourceName)
    }
}
