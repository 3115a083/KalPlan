package cc.stkmn.kalplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.application.Planner
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.CalendarEventRef
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.infrastructure.calendar.AndroidCalendarReader
import androidx.compose.ui.platform.LocalContext
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(state: AppData, repository: AppRepository, planner: Planner, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val day = LocalDate.parse(dayText)
    var events by remember { mutableStateOf(emptyList<CalendarEventRef>()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(day, state.calendars, state.requests) {
        try {
            val zone = ZoneId.systemDefault()
            val privacy = state.calendars.filter { it.included }.associateBy { it.id }
            events = AndroidCalendarReader(context).events(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(), privacy.keys).map { e ->
                val p = privacy.getValue(e.calendarId)
                e.copy(title = if (p.showTitle) e.title else null, location = if (p.showLocation) e.location else null, description = if (p.showDescription) e.description else null)
            }
            failed = false
        } catch (_: Exception) { failed = true; events = emptyList() }
    }
    val requests = state.requests.filter { r -> r.status != "DISMISSED" && r.candidate?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == day } == true }
    val colors = remember(state.calendars) { state.calendars.mapIndexed { i, p -> p.id to listOf(Color(0xFF4365DF), Color(0xFF208363), Color(0xFFAC5A20))[i % 3] }.toMap() }
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dayText = day.minusDays(7).toString() }) { Text("‹") }
                Text(day.format(DateTimeFormatter.ofPattern("MMMM yyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { dayText = day.plusDays(7).toString() }) { Text("›") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
                (0..6).forEach { offset ->
                    val date = monday.plusDays(offset.toLong())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelSmall)
                        FilterChip(selected = day == date, onClick = { dayText = date.toString() }, label = { Text(date.dayOfMonth.toString()) })
                    }
                }
            }
            if (state.calendars.none { it.included } || failed) Text(tr("Kalender auswählen und Leseberechtigung erteilen. Bis dahin ist die Machbarkeit unklar.", "Select calendars and grant read permission. Until then, feasibility is unknown."), color = MaterialTheme.colorScheme.error)
            if (requests.isEmpty() && events.isEmpty()) Text(tr("Keine Termine an diesem Tag.", "No appointments on this day."))
        }
        val zone = ZoneId.systemDefault()
        for (hour in 0..23) {
            val hourEvents = events.filter { it.start.atZone(zone).hour == hour || hour == 0 && it.start.atZone(zone).toLocalDate().isBefore(day) }
            val hourRequests = requests.filter { Instant.ofEpochMilli(it.candidate!!.startMillis!!).atZone(zone).hour == hour }
            if (hourEvents.isNotEmpty() || hourRequests.isNotEmpty() || hour in 8..20) {
                item(key = "hour-$hour") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("%02d:00".format(hour), modifier = Modifier.width(45.dp).padding(top = 10.dp), style = MaterialTheme.typography.labelSmall)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (hourEvents.isEmpty() && hourRequests.isEmpty()) HorizontalDivider(Modifier.padding(vertical = 16.dp))
                            hourEvents.forEach { e ->
                                val isReservation = state.requests.any { it.reservationEventId == e.id.substringBefore('@') }
                                val privacy = state.calendars.firstOrNull { it.id == e.calendarId }
                                val travel = privacy?.travelCalendar == true
                                var expanded by rememberSaveable(e.id) { mutableStateOf(false) }
                                Surface(modifier = Modifier.clickable(enabled = !e.description.isNullOrBlank()) { expanded = !expanded }, color = if (isReservation) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else (colors[e.calendarId] ?: MaterialTheme.colorScheme.secondary).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp), border = if (isReservation) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(e.start.atZone(zone).toLocalTime().toString() + " – " + e.end.atZone(zone).toLocalTime(), style = MaterialTheme.typography.labelLarge)
                                        Text((if (isReservation) tr("[Reserviert] ", "[Reserved] ") else if (travel) tr("Fahrt · ", "Travel · ") else "") + (e.title ?: tr("Belegt", "Busy")))
                                        e.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        if (!e.description.isNullOrBlank()) {
                                            Text(if (expanded) tr("Beschreibung ausblenden", "Hide description") else tr("Beschreibung anzeigen", "Show description"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                            if (expanded) Text(e.description.take(10_000), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            hourRequests.filterNot { it.reservationEventId != null && events.any { e -> e.id.substringBefore('@') == it.reservationEventId } }.forEach { r ->
                                val reserved = r.status in setOf("RESERVED", "RESERVATION_FAILED")
                                Card(onClick = { onOpen(r.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), colors = CardDefaults.cardColors(containerColor = if (reserved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (reserved) tr("[Reserviert]", "[Reserved]") else tr("Anfrage", "Request"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text(r.subject, fontWeight = FontWeight.SemiBold)
                                        LabelChips(r.labels, state.settings.labels, 3)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeScreen(state: AppData, planner: Planner, onOpen: (String) -> Unit, onAction: (String, String) -> Unit,
    onLater: (String) -> Unit, onUnclear: (String) -> Unit, onClose: () -> Unit) {
    var visited by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val requests = state.requests.filter { it.pending && it.id !in visited }.sortedByDescending { PlanningPolicy.priority(it, state.settings).score }
    val request = requests.firstOrNull()
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text(tr("Schließen", "Close")) }
            Text("${visited.size + if (request != null) 1 else 0} / ${visited.size + requests.size}")
        }
        if (request == null) {
            Text(tr("Alles gesichtet.", "All reviewed."), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { visited = emptyList() }) { Text(tr("Erneut ansehen", "Review again")) }
        } else {
            var drag by remember(request.id) { mutableStateOf(0f) }
            val threshold = 180f
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(request.id) {
                detectHorizontalDragGestures(onHorizontalDrag = { change, amount -> change.consume(); drag += amount }, onDragCancel = { drag = 0f }, onDragEnd = {
                    if (drag > threshold) onAction(request.id, "accept")
                    else if (drag < -threshold) { onLater(request.id); visited = visited + request.id }
                    drag = 0f
                })
            }, contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Später", "Later"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(tr("Prüfen", "Review"), color = Color(0xFF168A55), fontWeight = FontWeight.Bold)
                }
                Column(Modifier.fillMaxWidth().graphicsLayer { translationX = drag; rotationZ = drag / 90f }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RequestCard(request, state.settings, planner) { if (kotlin.math.abs(drag) < 8f) onOpen(request.id) }
                    Text(when { drag > 40 -> tr("Weiter nach rechts ziehen, um die Annahme zu prüfen", "Keep dragging right to review acceptance"); drag < -40 -> tr("Weiter nach links ziehen, um später zu entscheiden", "Keep dragging left to decide later"); else -> tr("Karte ziehen: links später, rechts prüfen. Ein Swipe sendet nie eine Mail.", "Drag the card: left for later, right to review. A swipe never sends mail.") }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { onLater(request.id); visited = visited + request.id }) { Text(tr("Später", "Later")) }
                OutlinedButton(onClick = { onUnclear(request.id); visited = visited + request.id }) { Text("?") }
                Button(onClick = { onAction(request.id, "accept") }) { Text(tr("Prüfen", "Review")) }
            }
        }
    }
}
