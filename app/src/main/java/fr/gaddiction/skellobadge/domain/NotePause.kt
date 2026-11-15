package fr.gaddiction.skellobadge.domain

import java.text.Normalizer
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pause rattachée à un créneau d'un seul tenant, et d'où on la tient.
 *
 * Une coupure lue dans le planning — l'écart entre deux créneaux — n'en est pas une : elle
 * se voit déjà dans les horaires. Celle-ci vit *à l'intérieur* d'un créneau, là où le
 * planning ne la montre pas.
 */
data class Pause(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val origin: Origin,
    /** Passage de la note qui a permis de la situer, tel qu'écrit par le responsable. */
    val excerpt: String? = null,
) {
    val duration: Duration get() = Duration.between(start, end)

    enum class Origin {
        /** Écrite en toutes lettres : « Pause cantine 12h-13h ». */
        NOTE,

        /** Déduite du trou entre deux activités datées : « Anim 1 : 9h-11h30 / Anim 2 : 13h-16h ». */
        NOTE_GAP,

        /** Rien dans la note : horaire par défaut des réglages. */
        DEFAULT,
    }
}

/**
 * Lit l'heure de pause dans la note d'un créneau.
 *
 * Les journées longues sont presque toujours d'un seul tenant au planning, alors que la
 * pause est bien prise — et que son heure est souvent écrite dans la note, au gré du
 * responsable : « Pause cantine 12h-13h », « Repas : 11h45 → 12h30 », « pause entre les
 * deux anims à 12h30 ». Quand la note énumère les activités avec leurs horaires sans
 * nommer la pause, c'est le trou entre deux d'entre elles qui la donne.
 *
 * Trois garde-fous rendent la lecture sûre sur du texte libre :
 * - seules comptent les heures qui tombent dans le créneau, ce qui écarte d'emblée les
 *   « Trajet : 8h », « Covoit : 7h20 » et autres durées (« 1h de route ») ;
 * - une pause doit commencer après l'entrée et finir avant la sortie ;
 * - sa durée reste plausible, entre [MIN_LENGTH] et [MAX_LENGTH].
 *
 * Faute de quoi on ne devine rien : le repli à heure fixe des réglages prend le relais.
 *
 * Entièrement pur, sans dépendance Android, pour être testé sur de vraies notes.
 */
object NotePause {

    /**
     * Mots qui signalent la pause dans une note, affichés tels quels dans les réglages.
     * Comparés sans accents ni casse : « Déj », « dej » et « DÉJ » se valent.
     */
    val DEFAULT_KEYWORDS: Set<String> = setOf(
        "pause",
        "cantine",
        "repas",
        "déj",
        "déjeuner",
        "manger",
        "coupure",
    )

    val MIN_LENGTH: Duration = Duration.ofMinutes(10)
    val MAX_LENGTH: Duration = Duration.ofHours(4)

    /** En dessous, une valeur horaire hors du créneau se lit comme une durée. */
    private const val MAX_DURATION_HOURS = 5

    /** Un trou plus court entre deux activités n'est qu'un battement, pas une pause. */
    private val MIN_GAP: Duration = Duration.ofMinutes(20)

    /**
     * « 12h », « 12h30 », « 12 h 30 », « 12:30 », mais pas le « 2 » de « Anim 2 : 13h »
     * ni les chiffres d'un numéro de téléphone. Le deux-points n'est reconnu que collé à
     * ses minutes, puisqu'il sert aussi à introduire une valeur.
     */
    private val TIME =
        Regex("""(?<![\d:])([01]?\d|2[0-3])(?:\s*h\s*([0-5]\d)?|:([0-5]\d))(?![\d])""")
    private val NOON = Regex("""\bmidi\b""")
    private val MINUTES = Regex("""(\d{1,3})\s*(?:min|mn)""")

    /**
     * Une note se découpe en passages : un par ligne, et plusieurs sur une même ligne
     * quand ils sont séparés par une virgule, un point-virgule ou une puce. La barre
     * oblique et le tiret n'en font pas partie : ils séparent aussi les deux bornes
     * d'un horaire (« 12h/13h », « 12h - 13h »).
     */
    private val SEGMENT_BREAK = Regex("""[\n;,|•]|\.(?=\s|$)""")

    fun find(
        blockStart: ZonedDateTime,
        blockEnd: ZonedDateTime,
        note: String?,
        keywords: Set<String> = DEFAULT_KEYWORDS,
        defaultLength: Duration = Duration.ofHours(1),
    ): Pause? {
        if (note.isNullOrBlank()) return null
        val span = Span(blockStart, blockEnd)
        val words = keywords.map(::normalize).map(String::trim).filter(String::isNotEmpty)
            .map(::keywordPattern)

        val segments = note.split(SEGMENT_BREAK)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { Segment(it, normalize(it)) }

        val (flagged, others) = segments.partition { segment ->
            words.any { it.containsMatchIn(segment.text) }
        }

        flagged.firstNotNullOfOrNull { explicitPause(it, words, span, defaultLength) }
            ?.let { return it }

        return gapBetweenActivities(others, span)
    }

    /** Pause nommée : la première heure qui suit le mot-clé en ouvre une, la suivante la ferme. */
    private fun explicitPause(
        segment: Segment,
        words: List<Regex>,
        span: Span,
        defaultLength: Duration,
    ): Pause? {
        val keywordAt = words.mapNotNull { it.find(segment.text)?.range?.first }.min()
        val all = times(segment.text)
        // « Anim 9h-11h puis pause 12h-13h » : ce sont les heures après le mot qui comptent.
        val after = all.filter { it.first > keywordAt }.ifEmpty { all }

        // Une petite valeur qui ne tombe pas dans le créneau est une durée — « Pause 1h30
        // à midi » —, pas une heure. Toute autre heure hors du créneau rend la pause
        // invalide : « Pause 8h-9h30 » sur un service de 9h n'a pas de sens à moitié.
        val (lengths, clock) = after.map { it.second }
            .partition { span.place(it) == null && it.hour < MAX_DURATION_HOURS }
        val start = clock.getOrNull(0)?.let(span::place) ?: return null
        val end = if (clock.size >= 2) {
            span.place(clock[1])?.takeIf { it.isAfter(start) } ?: return null
        } else {
            val stated = explicitLength(segment.text)
                ?: lengths.firstOrNull()?.let { Duration.ofMinutes(it.hour * 60L + it.minute) }
            start.plus(stated ?: defaultLength)
        }

        return Pause(start, end, Pause.Origin.NOTE, segment.original)
            .takeIf { span.holds(it) }
    }

    /**
     * Pause implicite : la note date au moins deux activités, et laisse un trou entre elles.
     * Le plus long l'emporte — un battement de vingt minutes entre deux ateliers du matin
     * ne doit pas masquer la vraie coupure du midi.
     */
    private fun gapBetweenActivities(segments: List<Segment>, span: Span): Pause? {
        // Une activité, ce sont les deux premières heures d'un passage, toutes deux dans le
        // créneau : « Covoit 7h20, sur place 9h30 » ne doit pas devenir un horaire.
        val ranges = segments.mapNotNull { segment ->
            val clock = times(segment.text).take(2).map { span.place(it.second) }
            val start = clock.getOrNull(0) ?: return@mapNotNull null
            val end = clock.getOrNull(1)?.takeIf { it.isAfter(start) } ?: return@mapNotNull null
            start to end
        }.sortedBy { it.first }

        if (ranges.size < 2) return null

        return ranges.zipWithNext { a, b -> a.second to b.first }
            .filter { (from, to) -> Duration.between(from, to) >= MIN_GAP }
            .maxByOrNull { (from, to) -> Duration.between(from, to) }
            ?.let { (from, to) -> Pause(from, to, Pause.Origin.NOTE_GAP) }
            ?.takeIf { span.holds(it) }
    }

    /** Chaque heure trouvée, avec sa position dans le texte. */
    private fun times(text: String): List<Pair<Int, LocalTime>> {
        val clock = TIME.findAll(text).map { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].ifEmpty { match.groupValues[3] }
                .ifEmpty { "0" }
                .toInt()
            match.range.first to LocalTime.of(hour, minute)
        }
        val noon = NOON.findAll(text).map { it.range.first to LocalTime.NOON }
        return (clock + noon).sortedBy { it.first }.toList()
    }

    /** « Pause 45 min à 12h » : la durée annoncée prime sur la durée par défaut. */
    private fun explicitLength(text: String): Duration? =
        MINUTES.find(text)?.groupValues?.get(1)?.toLongOrNull()?.let(Duration::ofMinutes)

    /**
     * Un mot-clé se cherche en début de mot : « pause » reconnaît « pauses », mais
     * « anim » ne doit pas surgir au milieu de « ranimer ». Un mot court doit en plus être
     * entier, sans quoi « dej » reconnaîtrait « déjà ».
     */
    private fun keywordPattern(word: String): Regex {
        val head = """(?<!\p{L})""" + Regex.escape(word)
        return Regex(if (word.length >= 4) head else head + """(?!\p{L})""")
    }

    /** Sans accents ni casse : la saisie dans Skello est libre et rarement soignée. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

    private class Segment(val original: String, val text: String)

    private class Span(val start: ZonedDateTime, val end: ZonedDateTime) {

        /**
         * Situe une heure de la note dans le créneau. Un créneau de soirée peut franchir
         * minuit : « 0h30 » appartient alors au lendemain.
         */
        fun place(time: LocalTime): ZonedDateTime? {
            val sameDay = start.toLocalDate().atTime(time).atZone(start.zone)
            return listOf(sameDay, sameDay.plusDays(1))
                .firstOrNull { !it.isBefore(start) && !it.isAfter(end) }
        }

        fun holds(pause: Pause): Boolean =
            pause.start.isAfter(start) &&
                pause.end.isBefore(end) &&
                pause.duration >= MIN_LENGTH &&
                pause.duration <= MAX_LENGTH
    }
}
