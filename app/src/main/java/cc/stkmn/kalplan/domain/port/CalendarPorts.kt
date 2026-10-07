package cc.stkmn.kalplan.domain.port

import java.time.Instant

data class CalendarRef(
    val id: String,
    val displayName: String,
    val colorArgb: Int?
)

data class CalendarEventRef(
    val id: String,
    val calendarId: String,
    val start: Instant,
    val end: Instant,
    val title: String?,
    val location: String?,
    val description: String?
)

data class ReservationDraft(
    val calendarId: String,
    val start: Instant,
    val end: Instant,
    val title: String,
    val location: String?,
    val description: String?
)

interface CalendarGateway {
    suspend fun calendars(): List<CalendarRef>
    suspend fun events(from: Instant, to: Instant, calendarIds: Set<String>): List<CalendarEventRef>
    suspend fun createReservation(draft: ReservationDraft): String
}
