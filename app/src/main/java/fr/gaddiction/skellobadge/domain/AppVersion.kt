package fr.gaddiction.skellobadge.domain

/**
 * Numéro de version tel que la CI le dérive de l'étiquette Git : `v1.4.0` devient `1.4.0`.
 *
 * La comparaison est numérique, composant par composant — `1.10.0` est bien plus récent
 * que `1.9.0`, ce qu'un tri alphabétique inverserait. Les composants manquants valent
 * zéro (`1.4` = `1.4.0`). Un suffixe de préversion (`1.5.0-beta1`) passe avant la version
 * finale du même numéro, comme le veut le versionnage sémantique.
 *
 * Pur et sans dépendance Android, pour être testé directement.
 */
data class AppVersion(
    val numbers: List<Int>,
    val preRelease: String? = null,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until size) {
            val diff = numbers.getOrElse(i) { 0 }.compareTo(other.numbers.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return when {
            preRelease == other.preRelease -> 0
            // Une préversion précède la version finale qu'elle annonce.
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String =
        numbers.joinToString(".") + (preRelease?.let { "-$it" } ?: "")

    companion object {

        private val PATTERN = Regex("""^[vV]?(\d+(?:\.\d+)*)(?:[-+](.+))?$""")

        /** Nul pour un libellé illisible : mieux vaut ne rien proposer que proposer à tort. */
        fun parse(raw: String?): AppVersion? {
            val match = PATTERN.matchEntire(raw?.trim().orEmpty()) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(numbers, match.groupValues[2].ifEmpty { null })
        }

        /**
         * La version publiée est-elle plus récente que celle installée ?
         *
         * Une version installée illisible — build local sans étiquette — ne bloque pas la
         * proposition : il vaut mieux la proposer à un développeur qu'oublier un utilisateur.
         */
        fun isNewer(published: String?, installed: String?): Boolean {
            val remote = parse(published) ?: return false
            val local = parse(installed) ?: return true
            return remote > local
        }
    }
}
