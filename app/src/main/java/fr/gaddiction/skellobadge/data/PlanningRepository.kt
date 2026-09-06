package fr.gaddiction.skellobadge.data

import android.content.Context
import fr.gaddiction.skellobadge.data.calendar.DeviceCalendarSource
import fr.gaddiction.skellobadge.data.ics.IcsFetcher
import fr.gaddiction.skellobadge.data.ics.IcsParser
import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.PlanningConfig
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.domain.PlanningEvent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Point d'entrée unique du planning : masque le fait qu'il provienne d'un flux ICS ou
 * du calendrier de l'appareil, et livre des journées déjà interprétées.
 */
class PlanningRepository(
    private val context: Context,
    private val fetcher: IcsFetcher = IcsFetcher(context),
    private val calendar: DeviceCalendarSource = DeviceCalendarSource(context),
) {

    data class Snapshot(
        val days: List<DayPlan>,
        val fromCache: Boolean = false,
        val error: String? = null,
        val recurringSkipped: Int = 0,
    )

    suspend fun load(
        settings: AppSettings,
        now: ZonedDateTime,
        horizon: Duration = DEFAULT_HORIZON,
    ): Snapshot {
        val zone = now.zone
        val config = settings.toPlanningConfig()

        return when (settings.source) {
            PlanningSource.ICS -> loadFromIcs(settings, now, horizon, zone, config)
            PlanningSource.DEVICE_CALENDAR -> loadFromCalendar(settings, now, horizon, zone, config)
        }
    }

    private suspend fun loadFromIcs(
        settings: AppSettings,
        now: ZonedDateTime,
        horizon: Duration,
        zone: ZoneId,
        config: PlanningConfig,
    ): Snapshot {
        val payload = fetcher.load(settings.icsUrl)
        val body = payload.body
            ?: return Snapshot(days = emptyList(), error = payload.error)

        val parsed = IcsParser.parse(body, zone)
        return Snapshot(
            days = PlanningEngine.build(parsed.events.withinWindow(now, horizon), config),
            fromCache = payload.fromCache,
            error = payload.error,
            recurringSkipped = parsed.recurringSkipped,
        )
    }

    private fun loadFromCalendar(
        settings: AppSettings,
        now: ZonedDateTime,
        horizon: Duration,
        zone: ZoneId,
        config: PlanningConfig,
    ): Snapshot {
        if (!calendar.hasPermission()) {
            return Snapshot(days = emptyList(), error = "Permission calendrier non accordée")
        }
        val events = calendar.events(
            calendarIds = settings.calendarIds,
            from = now.minusDays(1),
            to = now.plus(horizon),
            zone = zone,
        )
        return Snapshot(days = PlanningEngine.build(events, config))
    }

    /** On garde la veille pour que la journée en cours reste complète après minuit. */
    private fun List<PlanningEvent>.withinWindow(
        now: ZonedDateTime,
        horizon: Duration,
    ): List<PlanningEvent> {
        val from = now.minusDays(1)
        val to = now.plus(horizon)
        return filter { it.end.isAfter(from) && it.start.isBefore(to) }
    }

    private companion object {
        val DEFAULT_HORIZON: Duration = Duration.ofDays(21)
    }
}

/**
 * Traduit les préférences utilisateur en paramètres du moteur de planification.
 *
 * Aucun fuseau n'y figure : le moteur travaille dans celui porté par chaque créneau,
 * qui est celui de l'établissement.
 */
fun AppSettings.toPlanningConfig(): PlanningConfig = PlanningConfig(
    clockInLead = Duration.ofMinutes(clockInLeadMinutes.toLong()),
    breakInLead = Duration.ofMinutes(breakInLeadMinutes.toLong()),
    breakOutLead = Duration.ofMinutes(breakOutLeadMinutes.toLong()),
    clockOutLead = Duration.ofMinutes(clockOutLeadMinutes.toLong()),
    shiftChangeMaxGap = Duration.ofMinutes(shiftChangeMaxGapMinutes.toLong()),
    lunchFallbackEnabled = lunchFallbackEnabled,
    lunchFallbackOut = LocalTime.ofSecondOfDay(lunchStartMinutes * 60L),
    lunchFallbackIn = LocalTime.ofSecondOfDay(lunchEndMinutes * 60L),
    standbyPatterns = standbyPatterns,
    standbyAskEnabled = standbyAskEnabled,
    askTimeWorking = LocalTime.ofSecondOfDay(askWorkingMinutes * 60L),
    askTimeRest = LocalTime.ofSecondOfDay(askRestMinutes * 60L),
    disabledTypes = disabledShiftTypes,
    workingDates = workingStandbyDates.mapNotNull(::parseIsoDate).toSet(),
)

/** Une date illisible en préférence ne doit pas faire échouer toute la planification. */
private fun parseIsoDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()
