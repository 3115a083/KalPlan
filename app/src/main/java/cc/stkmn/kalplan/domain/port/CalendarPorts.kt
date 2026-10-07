package cc.stkmn.kalplan.domain.port

import java.time.Instant

data class CalendarRef(
    val id: String,
    val displayName: String,
    val colorArgb: Int?
)

enum class CalendarBusyStatus {
    BUSY,
    FREE,
    TENTATIVE,
    UNKNOWN
}

data class CalendarEventRef(
    val id: String,
    val calendarId: String,
    val start: Instant,
    val end: Instant,
    val title: String?,
    val location: String?,
    val description: String?,
    val allDay: Boolean,
    val busyStatus: CalendarBusyStatus
)

data class ReservationDraft(
    val calendarId: String,
    val start: Instant,
    val end: Instant,
    val title: String,
    val location: String?,
    val description: String?
)

interface CalendarReader {
    suspend fun calendars(): List<CalendarRef>

    suspend fun events(
        from: Instant,
        to: Instant,
        calendarIds: Set<String>
    ): List<CalendarEventRef>
}

interface ReservationWriter {
    suspend fun createReservation(draft: ReservationDraft): String
}
