package cc.stkmn.kalplan.application

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.*
import cc.stkmn.kalplan.domain.policy.*
import java.time.*

data class Assessment(
    val status: String,
    val reasons: List<String>,
    val events: List<CalendarEventRef>,
    val origin: String,
    val nextLocation: String = "",
    val travelEventIds: Set<String> = emptySet()
)

data class CalendarQueryWindow(val from: Instant, val to: Instant)

/** Pure planning decisions shared by list, detail and the final send recheck. */
object AppointmentAssessment {
    fun queryWindow(candidate: StoredCandidate, settings: Settings, zone: ZoneId): CalendarQueryWindow? {
        val start = candidate.startMillis?.let(Instant::ofEpochMilli) ?: return null
        val end = candidate.endMillis?.let(Instant::ofEpochMilli) ?: return null
        if (!end.isAfter(start)) return null
        val padding = maxOf(settings.originThreshold, settings.beforeBuffer, settings.afterBuffer, 0).toLong() * 60
        return CalendarQueryWindow(
            start.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().minusSeconds(padding),
            end.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(padding)
        )
    }

    fun evaluate(request: StoredRequest, data: AppData, calendarEvents: List<CalendarEventRef>, nowMillis: Long = System.currentTimeMillis()): Assessment {
        val settings = data.settings
        val candidate = request.candidate ?: return Assessment("UNKNOWN", listOf("select_candidate"), emptyList(), settings.originAddress)
        val start = candidate.startMillis?.let(Instant::ofEpochMilli)
            ?: return Assessment("UNKNOWN", listOf("missing_start"), emptyList(), settings.originAddress)
        val end = candidate.endMillis?.let(Instant::ofEpochMilli)
            ?: return Assessment("UNKNOWN", listOf("missing_end"), emptyList(), settings.originAddress)
        if (!end.isAfter(start)) return Assessment("UNKNOWN", listOf("invalid_interval"), emptyList(), settings.originAddress)
        val selected = data.calendars.filter { it.included }
        if (selected.isEmpty()) return Assessment("UNKNOWN", listOf("select_calendars"), emptyList(), settings.originAddress)
        val selectedIds = selected.map { it.id }.toSet()
        val raw = calendarEvents.filter { it.calendarId in selectedIds }.filterNot { event ->
            request.reservationEventId != null && event.id.substringBefore('@') == request.reservationEventId
        }
        val localReservations = data.requests.filter {
            it.id != request.id && it.status in setOf("RESERVED", "RESERVATION_FAILED") &&
                (it.reservationEventId == null || raw.none { e -> e.id.substringBefore('@') == it.reservationEventId })
        }.mapNotNull { r -> r.candidate?.let { c ->
            if (c.startMillis == null || c.endMillis == null) null else CalendarEventRef(
                "local:${r.id}", "local", Instant.ofEpochMilli(c.startMillis), Instant.ofEpochMilli(c.endMillis),
                "[Reserviert] ${r.subject}", null, null, false, CalendarBusyStatus.TENTATIVE
            )
        } }
        val events = raw + localReservations
        val conflict = TimeConflictEngine().evaluate(start, end, events, settings.beforeBuffer.toLong(), settings.afterBuffer.toLong())
        val previous = raw.filter { it.busyStatus != CalendarBusyStatus.FREE && !it.end.isAfter(start) }.maxByOrNull { it.end }
        val next = raw.filter { it.busyStatus != CalendarBusyStatus.FREE && !it.start.isBefore(end) }.minByOrNull { it.start }
        fun usableLocation(event: CalendarEventRef?): String? {
            event ?: return null
            val p = selected.firstOrNull { it.id == event.calendarId } ?: return null
            return event.location?.takeIf { it.isNotBlank() && (p.showLocation || p.useHiddenLocationForRouting) }
        }
        val previousRelevant = previous != null && Duration.between(previous.end, start).toMinutes() <= settings.originThreshold
        val origin = if (request.manualOrigin.isNotBlank()) request.manualOrigin else if (previousRelevant) usableLocation(previous) ?: settings.originAddress else settings.originAddress
        val destination = if (candidate.mode == "ONLINE") settings.originAddress else candidate.location
        val hasTravel = origin.isNotBlank() && destination.isNotBlank() && !origin.equals(destination, true)
        val afterLocation = request.manualAfterDestination.takeIf { it.isNotBlank() } ?: usableLocation(next)
        val afterTravel = afterLocation != null && destination.isNotBlank() && !destination.equals(afterLocation, true)
        val reasons = mutableListOf<String>()
        var status = when (conflict.status) {
            TimeConflictStatus.OVERLAP -> { reasons += "time_overlap"; "CONFLICT" }
            TimeConflictStatus.BUFFER_TIGHT -> { reasons += "buffer_tight"; "POSSIBLE" }
            TimeConflictStatus.CLEAR -> "FEASIBLE"
        }
        // Existing manual travel values are estimates, never silently treated as fresh provider data.
        val travel = request.travelMinutes
        if (status != "CONFLICT" && hasTravel) {
            if (travel != null && request.routeCheckedMillis != null && request.routeOrigin == origin && request.routeDestination == destination) {
                val gap = conflict.nearestGapBeforeMinutes
                if (gap != null && gap < travel + settings.beforeBuffer) { status = "CONFLICT"; reasons += "travel_before" }
                if (status != "CONFLICT" && nowMillis - request.routeCheckedMillis > 3_600_000) { status = "POSSIBLE"; reasons += "route_stale" }
            } else { status = "POSSIBLE"; reasons += "travel_unchecked" }
        }
        if (status != "CONFLICT" && afterTravel) {
            val afterMinutes = request.travelAfterMinutes
            val gap = conflict.nearestGapAfterMinutes
            if (afterMinutes != null && request.travelAfterCheckedMillis != null && request.routeAfterOrigin == destination && request.routeAfterDestination == afterLocation) {
                if (gap != null && gap < afterMinutes + settings.afterBuffer) { status = "CONFLICT"; reasons += "travel_after" }
                else if (nowMillis - request.travelAfterCheckedMillis > 3_600_000) { status = "POSSIBLE"; reasons += "route_stale" }
            } else { status = "POSSIBLE"; reasons += "travel_after_unchecked" }
        }
        if (status != "CONFLICT" && (candidate.mode == "UNKNOWN" || destination.isBlank() || origin.isBlank() ||
            previousRelevant && usableLocation(previous) == null && request.manualOrigin.isBlank() ||
            next != null && usableLocation(next) == null && request.manualAfterDestination.isBlank())) {
            status = "POSSIBLE"; reasons += "location_unclear"
        }
        val safeEvents = events.map { e ->
            val p = selected.firstOrNull { it.id == e.calendarId }
            e.copy(title = e.title.takeIf { p?.showTitle == true }, location = e.location.takeIf { p?.showLocation == true }, description = e.description.takeIf { p?.showDescription == true })
        }.sortedBy { it.start }
        val visibleOrigin = if (request.manualOrigin.isNotBlank()) origin else if (previousRelevant && previous != null && selected.firstOrNull { it.id == previous.calendarId }?.showLocation != true && usableLocation(previous) != null) "" else origin
        val nextVisible = if (request.manualAfterDestination.isNotBlank()) request.manualAfterDestination else
            afterLocation?.takeIf { selected.firstOrNull { p -> p.id == next?.calendarId }?.showLocation == true }.orEmpty()
        val travelEventIds = raw.filter { event ->
            selected.firstOrNull { it.id == event.calendarId }?.let { privacy ->
                privacy.travelCalendar && (privacy.travelTitleContains.isBlank() || event.title.orEmpty().contains(privacy.travelTitleContains, true))
            } == true
        }.mapTo(mutableSetOf()) { it.id }
        return Assessment(status, reasons.ifEmpty { listOf("time_clear") }, safeEvents, visibleOrigin, nextVisible, travelEventIds)
    }
}
