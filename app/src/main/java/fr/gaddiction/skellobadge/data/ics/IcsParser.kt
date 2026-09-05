package fr.gaddiction.skellobadge.data.ics

import fr.gaddiction.skellobadge.data.SkelloText
import fr.gaddiction.skellobadge.domain.PlanningEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Parseur ICS volontairement réduit au sous-ensemble de RFC 5545 qu'émet Skello :
 * des VEVENT non récurrents, datés avec un TZID explicite.
 *
 * Ce qui est géré : le dépliage des lignes, les paramètres de propriété, les trois
 * formes de date (TZID, UTC, VALUE=DATE), et le déséchappement des valeurs texte.
 *
 * Ce qui ne l'est pas : les récurrences (RRULE / RDATE / EXDATE). Le flux Skello n'en
 * contient aucune ; si une apparaît un jour, [ParseResult.recurringSkipped] le signale
 * pour qu'on l'affiche plutôt que d'ignorer l'événement en silence.
 */
object IcsParser {

    data class ParseResult(
        val events: List<PlanningEvent>,
        val recurringSkipped: Int = 0,
    )

    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(raw: String, fallbackZone: ZoneId): ParseResult {
        val lines = unfold(raw)
        val events = mutableListOf<PlanningEvent>()
        var recurring = 0

        var inEvent = false
        var current = mutableMapOf<String, Pair<Map<String, String>, String>>()

        for (line in lines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    current = mutableMapOf()
                }

                line.equals("END:VEVENT", ignoreCase = true) -> {
                    inEvent = false
                    if (current.containsKey("RRULE") || current.containsKey("RDATE")) {
                        recurring++
                    } else {
                        toEvent(current, fallbackZone)?.let(events::add)
                    }
                }

                inEvent -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val rawName = line.substring(0, colon)
                    val value = line.substring(colon + 1)
                    val parts = rawName.split(';')
                    val name = parts[0].uppercase()
                    val params = parts.drop(1).mapNotNull { p ->
                        val eq = p.indexOf('=')
                        if (eq <= 0) null
                        else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
                    }.toMap()
                    current[name] = params to value
                }
            }
        }
        return ParseResult(events.sortedBy { it.start }, recurring)
    }

    private fun toEvent(
        props: Map<String, Pair<Map<String, String>, String>>,
        fallbackZone: ZoneId,
    ): PlanningEvent? {
        val (startParams, startValue) = props["DTSTART"] ?: return null
        val start = parseDate(startParams, startValue, fallbackZone) ?: return null

        val end = props["DTEND"]
            ?.let { (p, v) -> parseDate(p, v, fallbackZone) }
            ?: props["DURATION"]?.second?.let { start.plus(parseDuration(it)) }
            ?: start

        val isDateOnly = startParams["VALUE"].equals("DATE", ignoreCase = true)
        val summary = props["SUMMARY"]?.second?.let(::unescape).orEmpty()
        val description = props["DESCRIPTION"]?.second?.let(::unescape)

        return PlanningEvent(
            uid = props["UID"]?.second ?: "${start.toEpochSecond()}-$summary",
            title = SkelloText.cleanTitle(summary),
            note = SkelloText.extractNote(description),
            start = start,
            end = end,
            allDayFlag = isDateOnly,
            url = props["URL"]?.second,
        )
    }

    private fun parseDate(
        params: Map<String, String>,
        value: String,
        fallbackZone: ZoneId,
    ): ZonedDateTime? = runCatching {
        when {
            params["VALUE"].equals("DATE", ignoreCase = true) ->
                LocalDate.parse(value, DATE_ONLY).atStartOfDay(fallbackZone)

            // Forme UTC : 20260824T060000Z
            value.endsWith("Z") ->
                LocalDateTime.parse(value.removeSuffix("Z"), DATE_TIME)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(fallbackZone)

            // Forme habituelle de Skello : DTSTART;TZID=Europe/Paris:20260824T081500
            else -> {
                val zone = params["TZID"]
                    ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                    ?: fallbackZone
                LocalDateTime.parse(value, DATE_TIME).atZone(zone)
            }
        }
    }.getOrNull()

    /** Sous-ensemble de la durée ISO-8601 utilisé par ICS (PT1H30M, P1D…). */
    private fun parseDuration(value: String): java.time.Duration = runCatching {
        java.time.Duration.parse(value)
    }.getOrElse { java.time.Duration.ZERO }

    /** RFC 5545 §3.1 : une ligne repliée reprend après un CRLF suivi d'une espace ou d'une tabulation. */
    private fun unfold(raw: String): List<String> {
        val out = mutableListOf<String>()
        val builder = StringBuilder()
        raw.split('\n').forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.startsWith(" ") || line.startsWith("\t")) {
                builder.append(line.substring(1))
            } else {
                if (builder.isNotEmpty()) out += builder.toString()
                builder.setLength(0)
                builder.append(line)
            }
        }
        if (builder.isNotEmpty()) out += builder.toString()
        return out
    }

    /** RFC 5545 §3.3.11 : desechappement des valeurs de type TEXT. */
    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    ',', ';', '\\' -> sb.append(next)
                    else -> {
                        sb.append(c)
                        sb.append(next)
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
