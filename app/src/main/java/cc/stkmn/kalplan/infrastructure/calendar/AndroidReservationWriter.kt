package cc.stkmn.kalplan.infrastructure.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import cc.stkmn.kalplan.domain.port.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

class AndroidReservationWriter(private val context: Context) : ReservationWriter {
    override suspend fun createReservation(draft: ReservationDraft): String = withContext(Dispatchers.IO) {
        require(!cc.stkmn.kalplan.data.AppRepository.get(context).data.value.settings.debug) { "Calendar writes disabled in debug mode" }
        require(draft.end.isAfter(draft.start))
        val calendar = draft.calendarId.toLongOrNull() ?: error("Invalid reservation calendar")
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL), "${CalendarContract.Calendars._ID} = ?", arrayOf(calendar.toString()), null)
            ?.use { c -> require(c.moveToFirst() && c.getInt(0) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) { "Calendar not writable" } }
            ?: error("Calendar unavailable")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar)
            put(CalendarContract.Events.DTSTART, draft.start.toEpochMilli())
            put(CalendarContract.Events.DTEND, draft.end.toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.TITLE, draft.title)
            put(CalendarContract.Events.EVENT_LOCATION, draft.location)
            put(CalendarContract.Events.DESCRIPTION, draft.description)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_TENTATIVE)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_TENTATIVE)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment ?: error("Reservation write failed")
    }
    suspend fun createLocalCalendar(): String = withContext(Dispatchers.IO) {
        require(!cc.stkmn.kalplan.data.AppRepository.get(context).data.value.settings.debug) { "Calendar writes disabled in debug mode" }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "KalPlan")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "KalPlan")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "kalplan-reservations")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "KalPlan Reservierungen")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFFBDB5FF.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "KalPlan")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        context.contentResolver.insert(uri, values)?.lastPathSegment ?: error("Local calendar unsupported")
    }
}
