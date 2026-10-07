package cc.stkmn.kalplan.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.*
import cc.stkmn.kalplan.domain.policy.*
import cc.stkmn.kalplan.infrastructure.calendar.AndroidCalendarReader
import java.time.*

data class Assessment(val status: String, val reasons: List<String>, val events: List<CalendarEventRef>, val origin: String)

class Planner(private val context: Context, private val repository: AppRepository) {
    suspend fun assess(request: StoredRequest): Assessment {
        val settings = repository.data.value.settings
        val candidate = request.candidate ?: return Assessment("UNKNOWN", listOf("select_candidate"), emptyList(), settings.originAddress)
        val start = candidate.startMillis?.let(Instant::ofEpochMilli)
            ?: return Assessment("UNKNOWN", listOf("missing_start"), emptyList(), settings.originAddress)
        val end = candidate.endMillis?.let(Instant::ofEpochMilli)
            ?: return Assessment("UNKNOWN", listOf("missing_end"), emptyList(), settings.originAddress)
        val selected = repository.data.value.calendars.filter { it.included }
        if (selected.isEmpty()) return Assessment("UNKNOWN", listOf("select_calendars"), emptyList(), settings.originAddress)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return Assessment("UNKNOWN", listOf("calendar_permission"), emptyList(), settings.originAddress)
        val zone = ZoneId.systemDefault()
        val day = start.atZone(zone).toLocalDate()
        val padding = maxOf(settings.originThreshold, settings.beforeBuffer, settings.afterBuffer).toLong()
        val raw = AndroidCalendarReader(context).events(
            day.atStartOfDay(zone).toInstant().minusSeconds(padding * 60),
            day.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(padding * 60),
            selected.map { it.id }.toSet()
        ).filterNot { event -> request.reservationEventId != null && event.id.substringBefore('@') == request.reservationEventId }
        val localReservations = repository.data.value.requests.filter {
            it.id != request.id && it.status in setOf("RESERVED", "RESERVATION_FAILED") && it.reservationEventId == null
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
        val origin = if (previousRelevant) usableLocation(previous) ?: settings.originAddress else settings.originAddress
        val destination = if (candidate.mode == "ONLINE") settings.originAddress else candidate.location
        val hasTravel = origin.isNotBlank() && destination.isNotBlank() && !origin.equals(destination, true)
        val afterLocation = usableLocation(next)
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
            if (travel != null && request.routeCheckedMillis != null) {
                val gap = conflict.nearestGapBeforeMinutes
                if (gap != null && gap < travel + settings.beforeBuffer) { status = "CONFLICT"; reasons += "travel_before" }
                if (status != "CONFLICT" && System.currentTimeMillis() - request.routeCheckedMillis > 3_600_000) { status = "POSSIBLE"; reasons += "route_stale" }
            } else { status = "POSSIBLE"; reasons += "travel_unchecked" }
        }
        if (status != "CONFLICT" && (afterTravel || candidate.mode == "UNKNOWN" || destination.isBlank())) {
            status = "POSSIBLE"; reasons += if (afterTravel) "travel_after_unchecked" else "location_unclear"
        }
        val safeEvents = events.map { e ->
            val p = selected.firstOrNull { it.id == e.calendarId }
            e.copy(title = e.title.takeIf { p?.showTitle == true }, location = e.location.takeIf { p?.showLocation == true }, description = e.description.takeIf { p?.showDescription == true })
        }.sortedBy { it.start }
        val visibleOrigin = if (previousRelevant && previous != null && selected.firstOrNull { it.id == previous.calendarId }?.showLocation != true && usableLocation(previous) != null) "" else origin
        return Assessment(status, reasons.ifEmpty { listOf("time_clear") }, safeEvents, visibleOrigin)
    }
}
