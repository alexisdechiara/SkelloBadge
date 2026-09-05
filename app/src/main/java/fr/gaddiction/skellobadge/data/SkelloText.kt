package fr.gaddiction.skellobadge.data

/**
 * Normalisation des libellés propres à Skello, partagée par les deux sources de
 * planning : le flux ICS et le calendrier de l'appareil produisent les mêmes chaînes.
 */
object SkelloText {

    /** Skello préfixe tous ses libellés par « Shift: ». */
    fun cleanTitle(summary: String?): String =
        summary?.removePrefix("Shift:")?.trim()?.ifEmpty { null } ?: "Service"

    /**
     * La description a la forme « <poste> @ <établissement>. Note: <texte libre> ».
     * Seule la note apporte de l'information : le reste duplique le titre et le lieu.
     */
    fun extractNote(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val marker = description.indexOf("Note:", ignoreCase = true)
        if (marker < 0) return null
        return description.substring(marker + 5).trim().ifEmpty { null }
    }
}
