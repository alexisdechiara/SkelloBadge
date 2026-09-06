package fr.gaddiction.skellobadge

import fr.gaddiction.skellobadge.domain.SyncHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/**
 * Le seuil décide quand l'application cesse de faire confiance au planning qu'elle
 * affiche. En dessous, tout ce qui relève du réseau ordinaire doit passer en silence.
 */
class SyncHealthTest {

    private val now = 1_800_000_000_000L

    private fun ago(duration: Duration): Long = now - duration.toMillis()

    @Test
    fun `a planning refreshed in the last hours raises nothing`() {
        assertNull(SyncHealth.staleFor(ago(Duration.ofHours(2)), now))
    }

    /** Une nuit en mode avion, un week-end sans réseau : rien à signaler. */
    @Test
    fun `a night without network stays silent`() {
        assertNull(SyncHealth.staleFor(ago(Duration.ofHours(12)), now))
    }

    @Test
    fun `the threshold itself already counts as stale`() {
        assertNotNull(SyncHealth.staleFor(ago(Duration.ofHours(24)), now))
    }

    @Test
    fun `a planning untouched for days is reported`() {
        val age = SyncHealth.staleFor(ago(Duration.ofDays(3)), now)
        assertEquals(Duration.ofDays(3), age)
    }

    /**
     * Aucune récupération n'a jamais abouti : c'est une configuration qui n'a pas encore
     * fonctionné, pas un flux qui se serait tari. L'installation guidée l'a déjà dit.
     */
    @Test
    fun `an installation that never synced raises nothing`() {
        assertNull(SyncHealth.staleFor(0L, now))
    }

    /** L'horloge peut reculer — changement d'heure, remise à l'heure réseau. */
    @Test
    fun `a clock going backwards raises nothing`() {
        assertNull(SyncHealth.staleFor(now + Duration.ofHours(6).toMillis(), now))
    }

    @Test
    fun `the age is spelled out in days`() {
        assertEquals("1 jour", SyncHealth.describe(Duration.ofHours(30)))
        assertEquals("2 jours", SyncHealth.describe(Duration.ofDays(2)))
        assertEquals("9 jours", SyncHealth.describe(Duration.ofDays(9).plusHours(4)))
    }
}
