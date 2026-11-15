package fr.gaddiction.skellobadge

import fr.gaddiction.skellobadge.domain.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparaison entre la version installée et l'étiquette de la dernière release GitHub.
 * Une erreur ici, c'est soit une mise à jour jamais proposée, soit une proposition qui
 * revient sans fin pour la version déjà installée.
 */
class AppVersionTest {

    @Test
    fun `a higher tag is newer than the installed version`() {
        assertTrue(AppVersion.isNewer("v1.2.0", "1.1.1"))
        assertTrue(AppVersion.isNewer("v2.0.0", "1.9.9"))
        assertTrue(AppVersion.isNewer("1.1.2", "1.1.1"))
    }

    @Test
    fun `the installed version itself is not proposed`() {
        assertFalse(AppVersion.isNewer("v1.1.1", "1.1.1"))
    }

    @Test
    fun `an older tag is never proposed`() {
        assertFalse(AppVersion.isNewer("v1.0.0", "1.1.1"))
    }

    /** Un tri alphabétique placerait 1.10 avant 1.9. */
    @Test
    fun `components are compared as numbers`() {
        assertTrue(AppVersion.isNewer("v1.10.0", "1.9.0"))
        assertFalse(AppVersion.isNewer("v1.9.0", "1.10.0"))
    }

    @Test
    fun `missing components count as zero`() {
        assertFalse(AppVersion.isNewer("v1.2", "1.2.0"))
        assertTrue(AppVersion.isNewer("v1.2.1", "1.2"))
    }

    @Test
    fun `a pre release comes before its final version`() {
        assertTrue(AppVersion.isNewer("v1.2.0", "1.2.0-beta1"))
        assertFalse(AppVersion.isNewer("v1.2.0-beta1", "1.2.0"))
        assertTrue(AppVersion.isNewer("v1.2.0-beta1", "1.1.9"))
    }

    @Test
    fun `an unreadable tag is never proposed`() {
        assertFalse(AppVersion.isNewer("nightly", "1.1.1"))
        assertFalse(AppVersion.isNewer(null, "1.1.1"))
        assertFalse(AppVersion.isNewer("", "1.1.1"))
    }

    /** Build local sans étiquette : la release publiée reste proposée. */
    @Test
    fun `an unreadable installed version still gets the release`() {
        assertTrue(AppVersion.isNewer("v1.2.0", null))
        assertTrue(AppVersion.isNewer("v1.2.0", "dev"))
    }

    @Test
    fun `parsing keeps numbers and suffix`() {
        assertEquals(AppVersion(listOf(1, 4, 0), "rc2"), AppVersion.parse("v1.4.0-rc2"))
        assertEquals("1.4.0-rc2", AppVersion.parse("V1.4.0-rc2").toString())
        assertNull(AppVersion.parse("v1..2"))
    }
}
