package cc.stkmn.kalplan.infrastructure.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import cc.stkmn.kalplan.domain.port.CalendarBusyStatus
import cc.stkmn.kalplan.domain.port.CalendarEventRef
import cc.stkmn.kalplan.domain.port.CalendarReader
import cc.stkmn.kalplan.domain.port.CalendarRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class AndroidCalendarReader(
    private val context: Context
) : CalendarReader {

    override suspend fun calendars(): List<CalendarRef> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )

        buildList {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getInt(3) == 0) continue
                    add(
                        CalendarRef(
                            id = cursor.getLong(0).toString(),
                            displayName = cursor.getString(1).orEmpty(),
                            colorArgb = if (cursor.isNull(2)) null else cursor.getInt(2)
                        )
                    )
                }
            }
        }
    }

    override suspend fun events(
        from: Instant,
        to: Instant,
        calendarIds: Set<String>
    ): List<CalendarEventRef> = withContext(Dispatchers.IO) {
        require(!to.isBefore(from)) { "to must be >= from" }
        if (calendarIds.isEmpty()) return@withContext emptyList()

        val numericIds = calendarIds.mapNotNull { it.toLongOrNull() }.toSet()
        if (numericIds.isEmpty()) return@withContext emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
            ContentUris.appendId(builder, from.toEpochMilli())
            ContentUris.appendId(builder, to.toEpochMilli())
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY
        )

        buildList {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendarId = cursor.getLong(1)
                    if (calendarId !in numericIds) continue

                    add(
                        CalendarEventRef(
                            id = cursor.getLong(0).toString() + "@" + cursor.getLong(2),
                            calendarId = calendarId.toString(),
                            start = Instant.ofEpochMilli(cursor.getLong(2)),
                            end = Instant.ofEpochMilli(cursor.getLong(3)),
                            title = cursor.getString(4),
                            location = cursor.getString(5),
                            description = cursor.getString(6),
                            allDay = cursor.getInt(7) != 0,
                            busyStatus = cursor.getInt(8).toBusyStatus()
                        )
                    )
                }
            }
        }
    }

    private fun Int.toBusyStatus(): CalendarBusyStatus = when (this) {
        CalendarContract.Events.AVAILABILITY_FREE -> CalendarBusyStatus.FREE
        CalendarContract.Events.AVAILABILITY_TENTATIVE -> CalendarBusyStatus.TENTATIVE
        CalendarContract.Events.AVAILABILITY_BUSY -> CalendarBusyStatus.BUSY
        else -> CalendarBusyStatus.UNKNOWN
    }
}
