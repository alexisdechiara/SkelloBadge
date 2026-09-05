package fr.gaddiction.skellobadge.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import fr.gaddiction.skellobadge.data.SkelloText
import fr.gaddiction.skellobadge.domain.PlanningEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Lecture du planning depuis le calendrier synchronisé sur l'appareil. */
class DeviceCalendarSource(private val context: Context) {

    data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val accountName: String,
    )

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CalendarInfo(
                            id = cursor.getLong(0),
                            displayName = cursor.getString(1) ?: "Calendrier",
                            accountName = cursor.getString(2) ?: "",
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    fun events(
        calendarIds: Set<Long>,
        from: ZonedDateTime,
        to: ZonedDateTime,
        zone: ZoneId,
    ): List<PlanningEvent> {
        if (!hasPermission() || calendarIds.isEmpty()) return emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { builder ->
                ContentUris.appendId(builder, from.toInstant().toEpochMilli())
                ContentUris.appendId(builder, to.toInstant().toEpochMilli())
            }
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val selection = CalendarContract.Instances.CALENDAR_ID +
            " IN (" + calendarIds.joinToString(",") + ")"

        return context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val begin = cursor.getLong(3)
                    val end = cursor.getLong(4)
                    add(
                        PlanningEvent(
                            uid = cursor.getLong(0).toString() + "@" + begin,
                            title = SkelloText.cleanTitle(cursor.getString(1)),
                            note = SkelloText.extractNote(cursor.getString(2)),
                            start = Instant.ofEpochMilli(begin).atZone(zone),
                            end = Instant.ofEpochMilli(end).atZone(zone),
                            allDayFlag = cursor.getInt(5) == 1,
                        ),
                    )
                }
            }
        }.orEmpty()
    }
}
