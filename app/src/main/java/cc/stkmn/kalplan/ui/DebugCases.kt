package cc.stkmn.kalplan.ui

import android.content.Context
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.infrastructure.calendar.AndroidCalendarReader
import java.time.*

/** Synthetic UI cases only. No business-specific names or real addresses are shipped. */
suspend fun generateDebugCases(context: Context, repository: AppRepository) {
    val state = repository.data.value
    require(state.settings.debug) { "Debug mode required" }
    val zone = ZoneId.systemDefault()
    val selected = state.calendars.filter { it.included }.map { it.id }.toSet()
    val from = LocalDate.now().atStartOfDay(zone).toInstant()
    val to = from.plus(Duration.ofDays(30))
    val events = runCatching { AndroidCalendarReader(context).events(from, to, selected) }.getOrDefault(emptyList())
    val anchor = events.firstOrNull { it.busyStatus != cc.stkmn.kalplan.domain.port.CalendarBusyStatus.FREE }?.start
        ?: LocalDate.now().plusDays(1).atTime(12, 0).atZone(zone).toInstant()
    fun request(index: Int, title: String, start: Instant?, minutes: Int, location: String, unclear: Boolean, travel: Int? = null, checked: Boolean = false): StoredRequest {
        val candidate = start?.let { StoredCandidate(it.toEpochMilli(), it.plusSeconds(minutes * 60L).toEpochMilli(), minutes, false, "DEBUG_SYNTHETIC", location, "ONSITE", if (unclear) 0.45 else 1.0) }
        return StoredRequest(
            id = "debug-${System.currentTimeMillis()}-$index", sender = "synthetic-$index@example.invalid", subject = title,
            body = "Künstlicher Debug-Fall $index. Keine echte Person, Firma oder Adresse.", receivedMillis = System.currentTimeMillis() - index * 3_600_000L,
            labels = listOf("Test $index"), candidates = candidate?.let(::listOf).orEmpty(), selectedCandidate = candidate?.let { 0 },
            issues = if (unclear) listOf("location_unclear") else emptyList(), unclear = unclear, demo = true,
            travelMinutes = travel, distanceKm = travel?.div(2.0), routeCheckedMillis = if (checked) System.currentTimeMillis() else null,
            routeOrigin = if (checked) state.settings.originAddress else "", routeDestination = if (checked) location else ""
        )
    }
    val cases = listOf(
        request(1, "Testauftrag, erreichbar", anchor.minusSeconds(3 * 3600), 60, "Testort Nord", false, 25, true),
        request(2, "Testauftrag, Zeitkonflikt", anchor, 90, "Testort Mitte", false),
        request(3, "Testauftrag, Fahrt ungeprüft", anchor.plusSeconds(3 * 3600), 60, "Testort Süd", false),
        request(4, "Testauftrag, zu weit entfernt", anchor.plusSeconds(6 * 3600), 45, "Testort Fern", false, 240, true),
        request(5, "Testauftrag, Angaben unklar", null, 60, "", true)
    )
    repository.update { it.copy(requests = it.requests.filterNot { request -> request.id.startsWith("debug-") } + cases) }
}
