package cc.stkmn.kalplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.application.Assessment
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import java.time.*

@Composable
fun SwipeDecisionCard(request: StoredRequest, state: AppData, assessment: Assessment?, onUnclear: () -> Unit = {}, onOpen: () -> Unit) {
    var expanded by rememberSaveable(request.id) { mutableStateOf(false) }
    val candidate = request.candidate ?: request.candidates.firstOrNull()
    val calendarFirst = state.settings.swipeDisplayModel == "CALENDAR_FIRST"
    val locationText = if (candidate?.mode in setOf("ONLINE", "HYBRID")) tr("Online", "Online") + " · " + state.settings.originAddress else candidate?.location.orEmpty().ifBlank { tr("Ort prüfen", "Review location") }
    ElevatedCard(Modifier.fillMaxSize(), shape = RoundedCornerShape(30.dp), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 9.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        StatusPill(if (request.unclear) "UNKNOWN" else assessment?.status ?: "UNKNOWN")
                    }
                    Text(appointmentTime(candidate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (!calendarFirst) {
                    Text(request.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppointmentKindIcon(candidate?.location, candidate?.mode)
                        Text(locationText, style = MaterialTheme.typography.bodyLarge)
                    }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (calendarFirst) {
                    candidate?.let { SwipeCalendarPreview(it, assessment) }
                    Text(request.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppointmentKindIcon(candidate?.location, candidate?.mode)
                        Text(locationText)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriorityMark(PlanningPolicy.priority(request, state.settings).score)
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                    Text("${candidate?.durationMinutes ?: "?"} min" + if (candidate?.assumed == true) tr(" · angenommen", " · assumed") else "")
                }
                LabelChips(request.labels, state.settings.labels)
                Text(request.sender, style = MaterialTheme.typography.bodySmall)
                if (request.candidates.size > 1 && request.candidate == null) Text(tr("Mehrere Terminvorschläge. Auswahl in Details erforderlich.", "Multiple candidates. Choose one in details."), color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.MailOutline, null, Modifier.size(18.dp)); Text(tr("Aus der Anfrage", "From the request"), style = MaterialTheme.typography.titleSmall) }
                Text(request.body, maxLines = if (expanded) Int.MAX_VALUE else 3, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) tr("Weniger anzeigen", "Show less") else tr("Inhalt ausklappen", "Expand content")); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
                if (!calendarFirst) candidate?.let { SwipeCalendarPreview(it, assessment) }
                assessment?.reasons?.filter { it != "time_clear" }?.forEach { reason ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Text(reasonText(reason), style = MaterialTheme.typography.bodySmall) }
                }
                if (assessment == null) Text(tr("Kalenderbewertung nicht verfügbar. Kalender und Berechtigung prüfen.", "Calendar assessment unavailable. Check calendar selection and permission."), style = MaterialTheme.typography.bodySmall)
                if (state.settings.value.enabled && candidate != null) {
                    val v = PlanningPolicy.value(candidate, request.travelMinutes, request.distanceKm, PlanningPolicy.valueSettings(request, state.settings))
                    Text(tr("Geschätzter Auftragswert", "Estimated order value"), style = MaterialTheme.typography.labelLarge)
                    Text(String.format(java.util.Locale.getDefault(), "≈ %.2f €", v.totalCents / 100.0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(tr("Arbeit: ", "Work: ") + "${(v.workCents + v.workFlatCents) / 100.0} € · " + tr("Fahrt: ", "Travel: ") + "${(v.travelCents + v.travelFlatCents) / 100.0} €", style = MaterialTheme.typography.bodySmall)
                }
                if (request.attachmentMeta.isNotEmpty()) Text("${request.attachmentMeta.size} " + tr("Anhänge", "attachments"), style = MaterialTheme.typography.bodySmall)
                Text(tr("Links: später · Rechts: Annahme prüfen. Es wird keine Mail gesendet.", "Left: later · Right: review acceptance. No mail is sent."), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpen) { Text(tr("Alle Details und Originalmail", "All details and source mail")); Icon(Icons.Outlined.OpenInNew, null) }
                TextButton(onClick = onUnclear) { Icon(Icons.Outlined.HelpOutline, null); Spacer(Modifier.width(6.dp)); Text(tr("Als unklar markieren", "Mark unclear")) }
                if (request.demo) Text(tr("Beispiel. Versand gesperrt.", "Sample. Sending disabled."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SwipeCalendarPreview(candidate: StoredCandidate, assessment: Assessment?) {
    val start = candidate.startMillis?.let(Instant::ofEpochMilli) ?: return
    val end = candidate.endMillis?.let(Instant::ofEpochMilli) ?: return
    if (end <= start) return
    val zone = ZoneId.systemDefault()
    val from = start.minusSeconds(3600)
    val to = minOf(end.plusSeconds(3600), start.plusSeconds(8 * 3600))
    val totalMinutes = Duration.between(from, to).toMinutes().coerceAtLeast(1)
    val context = assessment?.events.orEmpty().filter { it.start < to && it.end > from }
    Text(tr("Dein Kalender an dieser Stelle", "Your calendar at this time"), style = MaterialTheme.typography.titleSmall)
    if (Duration.between(start, end).toHours() > 6) Text(tr("Zeitausschnitt. Vollständigen Zeitraum in Details prüfen.", "Time excerpt. Review full interval in details."), style = MaterialTheme.typography.bodySmall)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(from.atZone(zone).toLocalTime().toString(), style = MaterialTheme.typography.labelSmall); Text(to.atZone(zone).toLocalTime().toString(), style = MaterialTheme.typography.labelSmall) }
            Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    context.forEach { event ->
                        val overlap = event.start < end && event.end > start && event.busyStatus != cc.stkmn.kalplan.domain.port.CalendarBusyStatus.FREE
                        val offset = Duration.between(from, maxOf(event.start, from)).toMinutes() / totalMinutes.toFloat()
                        val height = Duration.between(maxOf(event.start, from), minOf(event.end, to)).toMinutes() / totalMinutes.toFloat()
                        Surface(Modifier.fillMaxWidth().offset(y = 180.dp * offset).height((180.dp * height).coerceAtLeast(24.dp)),
                            color = if (overlap) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, if (overlap) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(if (overlap) Icons.Outlined.WarningAmber else Icons.Outlined.Event, if (overlap) tr("Kollision", "Collision") else null, Modifier.size(16.dp)); Text(event.title ?: tr("Belegt", "Busy"), style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                        }
                    }
                    if (context.isEmpty()) Text(if (assessment == null) tr("Nicht geprüft", "Unchecked") else tr("Keine Einträge im Ausschnitt", "No entries in excerpt"), style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val offset = Duration.between(from, start).toMinutes() / totalMinutes.toFloat()
                    val height = Duration.between(start, minOf(end, to)).toMinutes() / totalMinutes.toFloat()
                    Surface(Modifier.fillMaxWidth().offset(y = 180.dp * offset).height((180.dp * height).coerceAtLeast(30.dp)), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(7.dp)) { Text(tr("Diese Anfrage", "This request"), style = MaterialTheme.typography.labelMedium); Text(start.atZone(zone).toLocalTime().toString() + " – " + end.atZone(zone).toLocalTime(), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}
