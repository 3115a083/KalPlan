package cc.stkmn.kalplan.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(state: AppData, repository: AppRepository, planner: Planner, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val day = LocalDate.parse(dayText)
    var view by rememberSaveable { mutableStateOf("DAY") }
    val dayCount = when (view) { "THREE" -> 3; "WEEK" -> 7; else -> 1 }
    val shownDays = (0 until dayCount).map { day.plusDays(it.toLong()) }
    var events by remember { mutableStateOf(emptyList<CalendarEventRef>()) }
    var failed by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<CalendarEventRef?>(null) }
    LaunchedEffect(day, dayCount, state.calendars, state.requests) {
        try {
            val zone = ZoneId.systemDefault()
            val privacy = state.calendars.filter { it.included }.associateBy { it.id }
            events = AndroidCalendarReader(context).events(day.atStartOfDay(zone).toInstant(), day.plusDays(dayCount.toLong()).atStartOfDay(zone).toInstant(), privacy.keys).map { e ->
                val p = privacy.getValue(e.calendarId)
                e.copy(title = if (p.showTitle) e.title else null, location = if (p.showLocation) e.location else null, description = if (p.showDescription) e.description else null)
            }
            failed = false
        } catch (_: Exception) { failed = true; events = emptyList() }
    }
    val requests = state.requests.filter { r -> r.status != "DISMISSED" && r.candidate?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() in shownDays } == true }
    val colors = remember(state.calendars) { state.calendars.mapIndexed { i, p -> p.id to listOf(Color(0xFF4365DF), Color(0xFF208363), Color(0xFFAC5A20))[i % 3] }.toMap() }
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dayText = day.minusDays(dayCount.toLong()).toString() }) { Text("‹") }
                Text(day.format(DateTimeFormatter.ofPattern("MMMM yyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { dayText = day.plusDays(dayCount.toLong()).toString() }) { Text("›") }
            }
            ChoiceRow(listOf("DAY", "THREE", "WEEK"), view, label = { when (it) { "THREE" -> tr("3 Tage", "3 days"); "WEEK" -> tr("Woche", "Week"); else -> tr("Tag", "Day") } }) { view = it }
            Row(Modifier.fillMaxWidth().pointerInput(dayCount) {
                var drag = 0f
                detectHorizontalDragGestures(onHorizontalDrag = { change, amount -> change.consume(); drag += amount }, onDragEnd = {
                    if (kotlin.math.abs(drag) > 80f) dayText = (if (drag < 0) day.plusDays(dayCount.toLong()) else day.minusDays(dayCount.toLong())).toString()
                })
            }, horizontalArrangement = Arrangement.SpaceEvenly) {
                val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
                (0..6).forEach { offset ->
                    val date = monday.plusDays(offset.toLong())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelSmall)
                        FilterChip(selected = day == date, onClick = { dayText = date.toString() }, label = { Text(date.dayOfMonth.toString()) })
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            state.requests.filter { it.pending && it.candidate?.startMillis?.let { ms -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() == date } == true }
                                .flatMap { it.labels }.distinct().take(4).forEach { label ->
                                    Box(Modifier.size(5.dp).background(parseTimelineColor(state.settings.labels.firstOrNull { it.name == label }?.colorHex), RoundedCornerShape(50)))
                                }
                        }
                    }
                }
            }
            state.requests.filter { it.pending && it.candidate?.startMillis != null }.minByOrNull { it.candidate!!.startMillis!! }?.let { next ->
                OutlinedButton(onClick = { onOpen(next.id) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Nächste Entscheidung", "Next decision")); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.NavigateNext, null) }
            }
            if (state.calendars.none { it.included } || failed) Text(tr("Kalender auswählen und Leseberechtigung erteilen. Bis dahin ist die Machbarkeit unklar.", "Select calendars and grant read permission. Until then, feasibility is unknown."), color = MaterialTheme.colorScheme.error)
            if (requests.isEmpty() && events.isEmpty()) Text(tr("Keine Termine an diesem Tag.", "No appointments on this day."))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shownDays.forEach { date ->
                    TimelineDay(date, events, requests, state, colors, if (dayCount == 1) 330.dp else if (dayCount == 3) 210.dp else 150.dp, onOpen, onEvent = { selectedEvent = it })
                }
            }
        }
    }
    selectedEvent?.let { event -> AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text(event.title ?: tr("Kalendertermin", "Calendar event")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${event.start.atZone(ZoneId.systemDefault()).toLocalDateTime()} – ${event.end.atZone(ZoneId.systemDefault()).toLocalTime()}")
            event.location?.let { Text(it) }
            event.description?.let { SelectionContainer { Text(it.take(100_000)) } }
        }
    }, confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text(tr("Schließen", "Close")) } }) }
}

private fun parseTimelineColor(hex: String?) = runCatching { Color(android.graphics.Color.parseColor("#${hex.orEmpty().removePrefix("#")}")) }.getOrDefault(Color(0xFF6750A4))

@Composable
private fun TimelineDay(date: LocalDate, events: List<CalendarEventRef>, requests: List<StoredRequest>, state: AppData, colors: Map<String, Color>, width: androidx.compose.ui.unit.Dp, onOpen: (String) -> Unit, onEvent: (CalendarEventRef) -> Unit) {
    val zone = ZoneId.systemDefault(); val hourHeight = 52.dp
    Column(Modifier.width(width)) {
        Text(date.format(DateTimeFormatter.ofPattern("EEE, dd.MM.", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 44.dp, bottom = 6.dp))
        Box(Modifier.fillMaxWidth().height(hourHeight * 24)) {
            (0..23).forEach { hour ->
                Text("%02d:00".format(hour), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp).offset(y = hourHeight * hour))
                HorizontalDivider(Modifier.padding(start = 42.dp).offset(y = hourHeight * hour))
            }
            events.filter { it.start.atZone(zone).toLocalDate() == date || it.end.atZone(zone).toLocalDate() == date }.forEach { event ->
                val start = maxOf(event.start, date.atStartOfDay(zone).toInstant()); val end = minOf(event.end, date.plusDays(1).atStartOfDay(zone).toInstant())
                val startMinutes = Duration.between(date.atStartOfDay(zone).toInstant(), start).toMinutes().coerceIn(0, 1439)
                val duration = Duration.between(start, end).toMinutes().coerceAtLeast(1)
                val privacy = state.calendars.firstOrNull { it.id == event.calendarId }
                val travel = privacy?.travelCalendar == true && (privacy.travelTitleContains.isBlank() || event.title.orEmpty().contains(privacy.travelTitleContains, true))
                val reservation = state.requests.any { it.reservationEventId == event.id.substringBefore('@') }
                val lineColor = colors[event.calendarId] ?: MaterialTheme.colorScheme.secondary
                val modifier = Modifier.padding(start = 44.dp, end = 2.dp).fillMaxWidth().offset(y = hourHeight * (startMinutes / 60f)).height((hourHeight * (duration / 60f)).coerceAtLeast(30.dp)).clickable { onEvent(event) }
                    .then(if (travel) Modifier.drawBehind { drawRoundRect(lineColor, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())) } else Modifier)
                Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = if (reservation) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .75f) else lineColor.copy(alpha = .2f), border = if (!travel) BorderStroke(1.dp, lineColor) else null) {
                    Column(Modifier.padding(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("${start.atZone(zone).toLocalTime()}–${end.atZone(zone).toLocalTime()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); if (travel) Icon(Icons.Outlined.DirectionsCar, null, Modifier.size(15.dp)) }
                        Text((if (reservation) tr("Reserviert · ", "Reserved · ") else "") + (event.title ?: tr("Belegt", "Busy")), maxLines = if (duration > 75) 2 else 1, style = MaterialTheme.typography.bodySmall)
                        if (travel) Text(tr("⋯ führt zum Auftrag", "⋯ connects to order"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            requests.filter { it.candidate?.startMillis?.let { ms -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == date } == true && (it.reservationEventId == null || events.none { e -> e.id.substringBefore('@') == it.reservationEventId }) }.forEach { request ->
                val candidate = request.candidate!!; val start = Instant.ofEpochMilli(candidate.startMillis!!); val end = Instant.ofEpochMilli(candidate.endMillis ?: candidate.startMillis + candidate.durationMinutes * 60_000L)
                val startMinutes = Duration.between(date.atStartOfDay(zone).toInstant(), start).toMinutes().coerceIn(0, 1439); val duration = Duration.between(start, end).toMinutes().coerceAtLeast(1)
                Card(onClick = { onOpen(request.id) }, modifier = Modifier.padding(start = 48.dp, end = 5.dp).fillMaxWidth().offset(y = hourHeight * (startMinutes / 60f)).height((hourHeight * (duration / 60f)).coerceAtLeast(34.dp)), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .86f))) {
                    Column(Modifier.padding(6.dp)) { Text("${start.atZone(zone).toLocalTime()}–${end.atZone(zone).toLocalTime()}", style = MaterialTheme.typography.labelSmall); Text(request.subject, maxLines = 2, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
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
            val scope = rememberCoroutineScope()
            val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 150.dp.toPx() }
            fun springBack() { val start = drag; scope.launch { animate(start, 0f, animationSpec = tween(220)) { value, _ -> drag = value } } }
            val progress = (kotlin.math.abs(drag) / threshold).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(request.id) {
                detectHorizontalDragGestures(onHorizontalDrag = { change, amount -> change.consume(); drag = (drag + amount).coerceIn(-size.width * 0.8f, size.width * 0.8f) }, onDragCancel = { springBack() }, onDragEnd = {
                    if (drag > threshold) onAction(request.id, "accept")
                    else if (drag < -threshold) { onLater(request.id); visited = visited + request.id }
                    else springBack()
                })
            }, contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (drag < 0) 0.45f + progress * 0.5f else 0.18f)) {
                        Text("← " + tr("Später", "Later"), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFB9F3D2).copy(alpha = if (drag > 0) 0.45f + progress * 0.5f else 0.18f)) {
                        Text(tr("Prüfen", "Review") + " →", Modifier.padding(12.dp), color = Color(0xFF075C39), fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().graphicsLayer { translationX = drag; rotationZ = drag / threshold * 3f; shadowElevation = 6f + progress * 14f }) {
                        RequestCard(request, state.settings, planner) { if (kotlin.math.abs(drag) < 8f) onOpen(request.id) }
                    }
                    Text(when { drag > threshold * 0.25f -> tr("Weiter nach rechts ziehen, um die Annahme zu prüfen", "Keep dragging right to review acceptance"); drag < -threshold * 0.25f -> tr("Weiter nach links ziehen, um später zu entscheiden", "Keep dragging left to decide later"); else -> tr("Karte ziehen: links später, rechts prüfen. Ein Swipe sendet nie eine Mail.", "Drag the card: left for later, right to review. A swipe never sends mail.") }, style = MaterialTheme.typography.bodySmall)
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
