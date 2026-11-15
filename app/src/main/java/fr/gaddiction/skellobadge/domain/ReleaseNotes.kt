package fr.gaddiction.skellobadge.domain

/**
 * Notes de version, rendues lisibles dans l'application.
 *
 * Elles sont rédigées en Markdown dans CHANGELOG.md, puis reprises telles quelles comme
 * description de la release GitHub. L'application n'embarque pas de moteur Markdown pour
 * si peu : elle n'a besoin que de listes et de gras, qu'elle aplatit en texte simple.
 *
 * Les lignes que GitHub ajoute de lui-même — « Full Changelog », mentions de
 * contributeurs, liens de comparaison — ne disent rien à qui utilise l'application, et
 * sont écartées.
 */
object ReleaseNotes {

    data class Line(val text: String, val bullet: Boolean)

    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    /** Hors d'un mot seulement : « nom_de_fichier » garde ses tirets bas. */
    private val EMPHASIS = Regex("""(?<![\p{L}\d])(\*\*|__|\*|_|`)(.+?)\1(?![\p{L}\d])""")
    private val BULLET = Regex("""^\s*[-*+]\s+""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val NOISE = Regex("""(?i)full changelog|new contributors|made their first contribution|^https?://\S+$""")

    fun lines(markdown: String?): List<Line> {
        // Une puce peut courir sur plusieurs lignes du fichier : comme en Markdown, toute
        // ligne qui n'ouvre ni puce ni titre prolonge la précédente, jusqu'à une ligne vide.
        val items = mutableListOf<Pair<Boolean, StringBuilder>>()
        var open = false
        markdown.orEmpty().lines().forEach { raw ->
            when {
                raw.isBlank() || raw.trim() == "---" -> open = false
                NOISE.containsMatchIn(raw) -> open = false
                BULLET.containsMatchIn(raw) -> {
                    items += true to StringBuilder(raw.replace(BULLET, ""))
                    open = true
                }
                HEADING.containsMatchIn(raw) -> {
                    items += false to StringBuilder(raw.replace(HEADING, ""))
                    open = false
                }
                open -> items.last().second.append(' ').append(raw.trim())
                else -> {
                    items += false to StringBuilder(raw.trim())
                    open = true
                }
            }
        }
        return items
            .map { (bullet, text) ->
                Line(
                    text.toString().replace(LINK, "$1").replace(EMPHASIS, "$2").trim(),
                    bullet,
                )
            }
            .filter { it.text.isNotEmpty() }
    }

    /** Faut-il seulement proposer « Nouveautés » ? Pas pour des notes vides de sens. */
    fun hasContent(markdown: String?): Boolean = lines(markdown).isNotEmpty()
}
