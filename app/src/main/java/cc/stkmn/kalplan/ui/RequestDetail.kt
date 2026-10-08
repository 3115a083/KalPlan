package cc.stkmn.kalplan.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.application.*
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.extraction.*
import cc.stkmn.kalplan.infrastructure.reply.ReplyPolicy
import cc.stkmn.kalplan.infrastructure.routing.*
import java.time.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetail(request: StoredRequest, state: AppData, repository: AppRepository, planner: Planner, busy: Boolean,
    replyAction: String?, onAction: (String?) -> Unit, onRun: (suspend () -> Unit) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable(request.id) { mutableStateOf(0) }
    var assessment by remember(request.id) { mutableStateOf<Assessment?>(null) }
    var editing by rememberSaveable(request.id) { mutableStateOf(false) }
    var routing by rememberSaveable(request.id) { mutableStateOf(false) }
    var guided by rememberSaveable(request.id) { mutableStateOf(false) }
    var dismiss by remember { mutableStateOf(false) }
    var attachmentToOpen by remember { mutableStateOf<StoredAttachment?>(null) }
    LaunchedEffect(request, state.calendars, state.settings) { assessment = runCatching { planner.assess(request) }.getOrNull() }
    if (replyAction == "decline" && !state.settings.sendDeclineReply) {
        DiscardRequestConfirmation(request, busy, onCancel = { onAction(null) }, onConfirm = {
            onRun { repository.request(request.id) { it.copy(status = "DECLINED") }; onAction(null); onClose() }
        })
    } else if (replyAction != null) {
        ReplyComposer(request, replyAction == "accept", state, repository, busy, onRun, onDone = { onAction(null) }, onCancel = { onAction(null) })
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriorityMark(PlanningPolicy.priority(request, state.settings).score)
                StatusPill(if (request.pending) if (request.unclear) "UNKNOWN" else assessment?.status ?: "UNKNOWN" else request.status)
            }
            Text(request.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(request.sender, style = MaterialTheme.typography.bodySmall)
            LabelChips(request.labels, state.settings.labels)
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(tr("Übersicht", "Overview"), tr("E-Mail", "Email"), tr("Anhänge", "Files"), tr("Analyse", "Analysis")).forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, style = MaterialTheme.typography.labelMedium) }) }
            }
            when(tab) {
                0 -> {
                    if (request.candidates.isEmpty()) Text(tr("Kein vollständiger Termin erkannt. Ergänze die Angaben.", "No complete appointment found. Add the details."))
                    request.candidates.forEachIndexed { i, c ->
                        Card(onClick = { if (request.pending) onRun { repository.request(request.id) { it.copy(selectedCandidate = i, routeCheckedMillis = null, travelAfterCheckedMillis = null) } } },
                            border = BorderStroke(if (i == request.selectedCandidate) 2.dp else 1.dp, if (i == request.selectedCandidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(appointmentTime(c), fontWeight = FontWeight.SemiBold)
                                Text(if (c.assumed) "~${c.durationMinutes} min." else "${c.durationMinutes} min.")
                                Text(if (c.mode == "ONLINE") tr("Online · Arbeitsort: ", "Online · Work location: ") + state.settings.originAddress else c.location.ifBlank { tr("Ort fehlt", "Location missing") })
                                Text(tr("Konfidenz", "Confidence") + ": ${(c.confidence * 100).toInt()}% · ${c.relation}", style = MaterialTheme.typography.bodySmall)
                                if (i == request.selectedCandidate) Text(tr("Ausgewählt", "Selected"), color = MaterialTheme.colorScheme.primary)
                                c.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    if (request.pending) OutlinedButton(enabled = !busy, onClick = { editing = true }) { Text(tr("Angaben korrigieren", "Edit details")) }
                    assessment?.let { result ->
                        result.reasons.forEach { Text(reasonText(it), style = MaterialTheme.typography.bodyMedium) }
                        Text(tr("Tagesübersicht", "Day context"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (result.events.isEmpty()) Text(tr("Keine ausgewählten Termine in diesem Zeitraum.", "No selected events in this period."))
                        result.events.forEach { event -> CalendarContextEvent(event) }
                    }
                    if (state.settings.value.enabled && request.candidate != null) {
                        val value = PlanningPolicy.value(request.candidate!!, request.travelMinutes, request.distanceKm, PlanningPolicy.valueSettings(request, state.settings))
                        Text(tr("Auftragswert, Schätzung", "Estimated order value"), style = MaterialTheme.typography.titleMedium)
                        Text(String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "≈ %.2f €", value.totalCents / 100.0), style = MaterialTheme.typography.headlineMedium)
                        Text(tr("Arbeitszeit", "Work") + ": ${value.billedMinutes} min · ${value.workCents / 100.0} €\n" + tr("Fahrtzeit", "Travel") + ": ${value.travelCents / 100.0} €\n" + tr("Kilometer", "Distance") + ": ${value.distanceCents / 100.0} €\n" + tr("Pauschalen", "Flat fees") + ": ${value.flatCents / 100.0} €")
                    }
                    if (request.pending) {
                        OutlinedButton(enabled = !busy, onClick = { routing = true }) { Text(tr("Fahrt manuell prüfen", "Check travel manually")) }
                        request.travelMinutes?.let { Text("$it min · ${request.distanceKm ?: "?"} km · " + tr("Schätzung", "Estimate")) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = !busy, onClick = { onAction("accept") }, modifier = Modifier.weight(1f)) { Text(tr("Annehmen", "Accept")) }
                            OutlinedButton(enabled = !busy, onClick = { onAction("decline") }, modifier = Modifier.weight(1f)) { Text(tr("Ablehnen", "Decline")) }
                        }
                        OutlinedButton(enabled = !busy, onClick = { dismiss = true }) { Text(tr("Lokal entfernen", "Dismiss locally")) }
                    }
                }
                1 -> SelectionContainer { Text(request.body) }
                2 -> {
                    val files = request.attachmentMeta.filter { AttachmentPolicy.visible(it, state.settings) }
                    if (files.isEmpty()) Text(tr("Keine relevanten Anhänge.", "No relevant attachments."))
                    files.forEach { file -> ListItem(headlineContent = { Text(file.name) }, supportingContent = { Text(file.mime + " · " + (file.size?.let { "${it / 1024} KB" } ?: "?")) },
                        trailingContent = { TextButton(enabled = !busy && request.accountId.isNotBlank(), onClick = { attachmentToOpen = file }) { Text(tr("Öffnen", "Open")) } }) }
                    Text(tr("Anhänge werden nicht automatisch analysiert.", "Attachments are never analyzed automatically."), style = MaterialTheme.typography.bodySmall)
                }
                3 -> {
                    Text(tr("Deterministische Extraktion, lokal", "Deterministic extraction, local"), style = MaterialTheme.typography.titleMedium)
                    request.evidence.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    request.issues.forEach { Text(reasonText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(tr("Prioritätsregeln", "Priority rules"), style = MaterialTheme.typography.titleMedium)
                    PlanningPolicy.priority(request, state.settings).reasons.forEach { Text(it) }
                    Button(enabled = !busy, onClick = { guided = true }) { Text(tr("Profil aus dieser Mail erstellen", "Create profile from this email")) }
                    if (request.manual) Text(tr("Manuelle Angaben haben Vorrang und werden nicht durch Sync ersetzt.", "Manual edits take precedence and are preserved across sync."))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    attachmentToOpen?.let { file -> AlertDialog(onDismissRequest = { attachmentToOpen = null }, title = { Text(tr("Anhang extern öffnen?", "Open attachment externally?")) },
        text = { Text(file.name + "\n" + tr("Die gewählte Viewer-App erhält Zugriff auf diese Datei. Maximal 10 MB, keine automatische Analyse.", "The selected viewer app will receive access to this file. Maximum 10 MB, no automatic analysis.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { attachmentToOpen = null; onRun {
            val directory = java.io.File(context.cacheDir, "attachments").apply { mkdirs() }
            directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
            val extension = when(file.mime.lowercase()) { "application/pdf" -> ".pdf"; "image/png" -> ".png"; "image/jpeg" -> ".jpg"; "text/plain" -> ".txt"; else -> ".bin" }
            val target = java.io.File(directory, UUID.randomUUID().toString() + extension)
            val providers = cc.stkmn.kalplan.infrastructure.mail.AccountProviders(repository)
            cc.stkmn.kalplan.infrastructure.mail.AngusMailReader(providers, providers).downloadAttachment(cc.stkmn.kalplan.domain.port.MailFolderRef(request.accountId, request.folder), request.sourceStableId ?: request.id, file.partPath, target)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", target)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime)
                .apply { clipData = android.content.ClipData.newRawUri("attachment", uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), file.name))
        } }) { Text(tr("Laden und öffnen", "Download and open")) } }, dismissButton = { TextButton(onClick = { attachmentToOpen = null }) { Text(tr("Abbrechen", "Cancel")) } }) }
    if (editing) EditCandidateDialog(request, state.settings.labels, onDismiss = { editing = false }, onSave = { c, labels -> editing = false; onRun { repository.request(request.id) { it.copy(candidates = listOf(c), selectedCandidate = 0, unclear = false, manual = true, labels = labels, issues = emptyList(), status = "NEW", routeCheckedMillis = null, travelAfterCheckedMillis = null) } } })
    if (routing) RouteDialog(request, state, assessment?.origin ?: state.settings.originAddress, assessment?.nextLocation.orEmpty(), repository, busy, onRun, onDismiss = { routing = false })
    if (guided) GuidedProfileDialog(request, state, onDismiss = { guided = false }, onSave = { profile -> guided = false; onRun { repository.update { it.copy(profiles = it.profiles.filterNot { p -> p.id == profile.id } + profile) } } })
    if (dismiss) AlertDialog(onDismissRequest = { dismiss = false }, title = { Text(tr("Aus KalPlan entfernen?", "Dismiss from KalPlan?")) }, text = { Text(tr("Die Quellmail bleibt erhalten. Der Eintrag bleibt im Verlauf.", "The source email is preserved. The item stays in history.")) }, confirmButton = { TextButton(onClick = { dismiss = false; onRun { repository.request(request.id) { it.copy(status = "DISMISSED") }; onClose() } }) { Text(tr("Entfernen", "Dismiss")) } }, dismissButton = { TextButton(onClick = { dismiss = false }) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EditCandidateDialog(request: StoredRequest, policies: List<LabelPolicy>, onDismiss: () -> Unit, onSave: (StoredCandidate, List<String>) -> Unit) {
    val c = request.candidate ?: request.candidates.firstOrNull()
    val initial = c?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val inputLocale = when (LocalAppLanguage.current) { "DE" -> java.util.Locale.GERMANY; "EN" -> java.util.Locale.US; else -> java.util.Locale.getDefault() }
    var date by rememberSaveable { mutableStateOf(initial?.toLocalDate()?.let { localDateText(it, inputLocale) }.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(initial?.toLocalTime()?.toString()?.take(5).orEmpty()) }
    var offsetText by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf((c?.durationMinutes ?: 60).toString()) }
    var location by rememberSaveable { mutableStateOf(c?.location.orEmpty()) }
    var online by rememberSaveable { mutableStateOf(c?.mode == "ONLINE") }
    var labels by rememberSaveable { mutableStateOf(request.labels) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Termin bestätigen", "Confirm appointment")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Prüfe Datum, Jahr und Uhrzeit anhand der Originalmail.", "Verify date, year and time against the original email."))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                SelectionContainer { Text(request.body.take(12_000), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }
            EditField(tr("Datum (TT.MM.JJJJ)", "Date (MM/DD/YYYY)"), date) { date = it }
            EditField(tr("Uhrzeit (HH:MM)", "Time (HH:MM)"), time) { time = it }
            EditField(tr("UTC-Offset bei Zeitumstellung, z. B. +02:00. Sonst leer.", "UTC offset for DST overlap, e.g. +02:00. Otherwise empty."), offsetText) { offsetText = it }
            EditField(tr("Dauer in Minuten", "Duration in minutes"), duration) { duration = it }
            EditField(tr("Ort", "Location"), location) { location = it }
            ToggleRow(tr("Online", "Online"), online) { online = it }
            Text(tr("Labels", "Labels"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                policies.forEach { policy -> FilterChip(selected = policy.name in labels, onClick = {
                    labels = if (policy.name in labels) labels - policy.name else labels + policy.name
                }, label = { Text(policy.name) }) }
            }
            if (error) Text(tr("Ungültige oder mehrdeutige Zeit. Sommerzeit prüfen.", "Invalid or ambiguous time. Check daylight saving time."), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            val local = parseLocalDateText(date, inputLocale).atTime(LocalTime.parse(time))
            val offsets = ZoneId.systemDefault().rules.getValidOffsets(local)
            val offset = if (offsetText.isBlank()) { require(offsets.size == 1); offsets.single() } else {
                ZoneOffset.of(offsetText).also { require(it in offsets) }
            }
            val start = local.atOffset(offset).toInstant().toEpochMilli()
            val minutes = duration.toInt(); require(minutes in 1..10080)
            onSave(StoredCandidate(start, start + minutes * 60_000L, minutes, false, "USER_OVERRIDE", location, if (online) "ONLINE" else "ONSITE", 1.0), labels.distinct().take(30))
        }.onFailure { error = true }
    }) { Text(tr("Angaben bestätigen", "Confirm details")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
private fun CalendarContextEvent(event: cc.stkmn.kalplan.domain.port.CalendarEventRef) {
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    Card(onClick = { if (!event.description.isNullOrBlank()) expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.title ?: tr("Belegt, Details verborgen", "Busy, details hidden"), fontWeight = FontWeight.SemiBold)
            Text(event.start.atZone(zone).toLocalTime().toString() + " – " + event.end.atZone(zone).toLocalTime(), style = MaterialTheme.typography.labelMedium)
            event.location?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!event.description.isNullOrBlank()) {
                Text(if (expanded) tr("Beschreibung ausblenden", "Hide description") else tr("Beschreibung anzeigen", "Show description"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                if (expanded) SelectionContainer { Text(event.description.take(10_000), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DiscardRequestConfirmation(request: StoredRequest, busy: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text(tr("Anfrage ablehnen?", "Decline request?")) },
        text = { Text(tr("Die Anfrage wird in KalPlan verworfen. Es wird keine Nachricht gesendet und die Quellmail bleibt unverändert.", "The request will be dismissed in KalPlan. No message is sent and the source email remains unchanged.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text(tr("Ohne Nachricht ablehnen", "Decline without message")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
private fun ReplyComposer(request: StoredRequest, accept: Boolean, state: AppData, repository: AppRepository, busy: Boolean,
    onRun: (suspend () -> Unit) -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val settings = state.settings
    val candidate = request.candidate
    val profileId = state.accounts.firstOrNull { it.id == request.accountId }?.folderProfiles?.get(request.folder)
    val profile = state.profiles.firstOrNull { it.id == profileId && it.enabled }
    val defaultBody = (if (accept) profile?.acceptTemplate?.takeIf { it.isNotBlank() } ?: settings.acceptTemplate else profile?.declineTemplate?.takeIf { it.isNotBlank() } ?: settings.declineTemplate)
        .replace("{date}", candidate?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty())
        .replace("{time}", candidate?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().toString() }.orEmpty())
        .replace("{subject}", request.subject).replace("{sender}", request.sender)
    var body by rememberSaveable(request.id, accept) { mutableStateOf(defaultBody) }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val recipient = runCatching { ReplyPolicy.recipient(request, settings) }.getOrNull()
    val simulation = request.demo || settings.debug && !settings.debugSendToTest
    val allowed = !busy && (simulation || recipient != null && ReplyPolicy.canSend(request)) && (!accept || request.candidate != null && !request.unclear)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (accept) tr("Annahme vorbereiten", "Prepare acceptance") else tr("Absage vorbereiten", "Prepare decline"), style = MaterialTheme.typography.headlineSmall)
        Text(tr("Empfänger: ", "Recipient: ") + (recipient ?: if (simulation) tr("Simulation, kein Versand", "Simulation, no send") else tr("Ungültig. Konto prüfen.", "Invalid. Check account.")), fontWeight = FontWeight.Bold)
        Text(appointmentTime(candidate))
        if (settings.debug) Text(tr("Debug: Versand nur an Testadresse. Kalenderwrites werden simuliert.", "Debug: only the test address can receive mail. Calendar writes are simulated."), color = MaterialTheme.colorScheme.error)
        if (request.accountId.isBlank() && !request.demo) Text(tr("Dieser lokale Import hat keine Quellmail. Du kannst den Text für eine manuelle Antwort kopieren.", "This local import has no source email. Copy the text for a manual reply."))
        OutlinedTextField(body, { body = it.take(100_000) }, modifier = Modifier.fillMaxWidth(), minLines = 8, label = { Text(tr("Antworttext", "Reply text")) })
        state.accounts.firstOrNull { it.id == request.accountId }?.signature?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (accept) Text(tr("Nach Versand: wartet auf Rückmeldung. Eine optionale Reservierung ist kein bestätigter Auftrag.", "After sending: waiting for response. An optional reservation is not a confirmed booking."))
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(body)) }) { Text(tr("Text kopieren", "Copy text")) }
        Button(enabled = allowed && body.isNotBlank(), onClick = { confirm = true }) { Text(if (simulation) tr("Simulation prüfen", "Review simulation") else tr("Senden prüfen", "Review send")) }
        TextButton(enabled = !busy, onClick = onCancel) { Text(tr("Zurück", "Back")) }
        result?.let { Text(when(it) {
            "send_blocked" -> tr("Versand vor der Übertragung gestoppt. Kalender und Kontoeinstellungen prüfen.", "Sending stopped before data transfer. Check calendars and account settings.")
            "delivery_unknown" -> tr("Versand ungewiss. Bitte zuerst im Mailkonto prüfen. Erneutes Senden ist gesperrt.", "Delivery uncertain. Check your mail account first. Resending is blocked.")
            "reservation_failed" -> tr("Antwort wurde gesendet. Kalenderreservierung ist fehlgeschlagen. Nicht erneut senden.", "Reply was sent. Calendar reservation failed. Do not resend.")
            "debug_simulated", "demo_no_send" -> tr("Simulation abgeschlossen. Keine Mail versendet, kein Kalender geändert.", "Simulation complete. No email sent or calendar changed.")
            else -> tr("Antwort wurde gesendet.", "Reply sent.")
        }, color = MaterialTheme.colorScheme.primary) }
    }
    if (confirm) AlertDialog(onDismissRequest = { if (!busy) confirm = false }, title = { Text(tr("Versand bestätigen", "Confirm send")) },
        text = { Text((if (accept) tr("Annehmen", "Accept") else tr("Ablehnen", "Decline")) + "\n${recipient.orEmpty()}\n${appointmentTime(candidate)}\n\n" + tr("Vor einer Annahme prüft KalPlan den Kalender erneut. Bei Konflikten, ungeklärter Fahrt oder fehlendem Zugriff stoppt der Versand.", "Before acceptance, KalPlan checks the calendar again. Conflicts, unchecked travel or missing access stop sending.")) },
        confirmButton = { TextButton(enabled = allowed, onClick = { confirm = false; onRun { result = ReplyCoordinator(context, repository).send(request.id, accept, body, recipient.orEmpty(), request) } }) { Text(if (simulation) tr("Simulation bestätigen", "Confirm simulation") else tr("Jetzt senden", "Send now")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
private fun RouteDialog(request: StoredRequest, state: AppData, originAddress: String, nextLocation: String, repository: AppRepository, busy: Boolean,
    onRun: (suspend () -> Unit) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var origin by rememberSaveable { mutableStateOf(originAddress) }
    var destination by rememberSaveable { mutableStateOf(if (request.candidate?.mode == "ONLINE") state.settings.originAddress else request.candidate?.location.orEmpty()) }
    var afterDestination by rememberSaveable { mutableStateOf(request.manualAfterDestination.ifBlank { nextLocation }) }
    var afterMinutes by rememberSaveable { mutableStateOf(request.travelAfterMinutes?.toString().orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(request.travelMinutes?.toString().orEmpty()) }
    var km by rememberSaveable { mutableStateOf(request.distanceKm?.toString().orEmpty()) }
    var provider by rememberSaveable { mutableStateOf(state.settings.routingProvider.takeIf { it in listOf("GOOGLE", "HERE", "TOMTOM", "ORS", "GRAPHHOPPER") } ?: "ORS") }
    var key by remember { mutableStateOf("") }
    var oCoordinates by rememberSaveable { mutableStateOf("") }
    var dCoordinates by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(tr("Fahrtzeit manuell", "Manual travel check")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(tr("Startort", "Origin"), origin) { origin = it }
            Text(tr("Ziel: ", "Destination: ") + destination.ifBlank { tr("Fehlt. Zuerst Termindetails korrigieren.", "Missing. Correct appointment details first.") })
            Text(tr("Karte öffnen überträgt die beiden Adressen an Google Maps.", "Opening the map sends both addresses to Google Maps."), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = origin.isNotBlank() && destination.isNotBlank(), onClick = {
                val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon().appendQueryParameter("api", "1").appendQueryParameter("origin", origin).appendQueryParameter("destination", destination).appendQueryParameter("travelmode", "driving").build()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) { Text(tr("Karte öffnen", "Open map")) }
            Text(tr("Oder API-Abfrage: nur diese Koordinaten verlassen das Gerät. Kein automatisches Geocoding. Ergebnis ist eine Schätzung ohne garantierte Verkehrsprognose.", "Or query an API: only these coordinates leave the device. No automatic geocoding. Results are estimates without guaranteed traffic prediction."))
            ChoiceRow(listOf("GOOGLE", "HERE", "TOMTOM", "ORS", "GRAPHHOPPER"), provider) { provider = it }
            EditField(tr("Start: Breitengrad,Längengrad", "Origin: latitude,longitude"), oCoordinates) { oCoordinates = it }
            EditField(tr("Ziel: Breitengrad,Längengrad", "Destination: latitude,longitude"), dCoordinates) { dCoordinates = it }
            SecretField("API-Key", key) { key = it }
            Button(enabled = !busy, onClick = { onRun {
                val apiKey = key.ifBlank { repository.routingKey(provider) }
                if (key.isNotBlank()) repository.saveRoutingKey(provider, key)
                val result = ManualRouting(repository).route(provider, RoutePoint.parse(oCoordinates), RoutePoint.parse(dCoordinates), apiKey)
                minutes = result.minutes.toString(); km = result.km.toString()
            } }) { Text(tr("Kostenpflichtige Abfrage auslösen", "Run metered API query")) }
            Text(tr("Tageslimit je Anbieter: ", "Daily limit per provider: ") + state.settings.routingDailyLimit)
            EditField(tr("Fahrtminuten zum Termin", "Travel minutes to appointment"), minutes) { minutes = it }
            EditField(tr("Entfernung, km", "Distance, km"), km) { km = it }
            OutlinedButton(enabled = !busy, onClick = {
                onRun {
                    val o = RoutePoint.parse(oCoordinates); val d = RoutePoint.parse(dCoordinates)
                    val estimate = cc.stkmn.kalplan.domain.proximity.ApproximateTravelEstimator().estimate(
                        cc.stkmn.kalplan.domain.model.GeoPoint(o.latitude, o.longitude), cc.stkmn.kalplan.domain.model.GeoPoint(d.latitude, d.longitude))
                    minutes = estimate.estimatedMinutesMax.toString(); km = estimate.estimatedRoadKmMax.toString()
                }
            }) { Text(tr("Grobe Offline-Näherung aus Koordinaten", "Coarse offline estimate from coordinates")) }
            Text(tr("Die Offline-Näherung kennt keine Straßen oder Hindernisse. Prüfe die Fahrt selbst.", "The offline estimate does not know roads or obstacles. Review the trip yourself."), style = MaterialTheme.typography.bodySmall)
            EditField(tr("Ziel des Folgetermins, falls vorhanden", "Following appointment destination, if any"), afterDestination) { afterDestination = it }
            EditField(tr("Fahrtminuten zum Folgetermin", "Travel minutes to following appointment"), afterMinutes) { afterMinutes = it }
        }
    }, confirmButton = { TextButton(enabled = !busy && minutes.toIntOrNull()?.let { it in 0..10080 } == true && (km.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..100_000.0 } == true), onClick = { onRun { repository.request(request.id) { it.copy(travelMinutes = minutes.toInt(), distanceKm = km.toDouble(), routeCheckedMillis = System.currentTimeMillis(),
                manualOrigin = origin, manualAfterDestination = afterDestination,
                routeOrigin = origin, routeDestination = destination, routeAfterOrigin = destination, routeAfterDestination = afterDestination,
                travelAfterMinutes = afterMinutes.toIntOrNull()?.also { n -> require(n in 0..10080) },
                travelAfterCheckedMillis = if (afterMinutes.toIntOrNull() != null) System.currentTimeMillis() else null) }; onDismiss() } }) { Text(tr("Schätzung übernehmen", "Use estimate")) } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Schließen", "Close")) } })
}

@Composable
private fun GuidedProfileDialog(request: StoredRequest, state: AppData, onDismiss: () -> Unit, onSave: (ExtractionProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(request.subject.take(60)) }
    var acceptOverride by rememberSaveable { mutableStateOf("") }
    var declineOverride by rememberSaveable { mutableStateOf("") }
    var rules by remember { mutableStateOf(emptyList<ExtractorRule>()) }
    val input = remember(request.id) { ExtractionInput(request.sender, request.subject, request.body, Instant.ofEpochMilli(request.receivedMillis)) }
    val candidates = remember(input) { GuidedRuleFactory.candidates(input) }
    var preview by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Geführtes Extraktionsprofil", "Guided extraction profile")) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(tr("Profilname", "Profile name"), name) { name = it }
            Text(tr("Ordne erkannte Zeilen einem Feld zu. Regeln werden erst nach Speichern und Zuordnung zu einem Mailordner aktiv.", "Assign detected lines to a field. Rules become active after saving and assigning a mail folder."))
            candidates.forEachIndexed { i, c ->
                Text("${c.label}: ${c.value.take(100)}", style = MaterialTheme.typography.bodySmall)
                var semantic by remember(i) { mutableStateOf("CUSTOM") }
                ChoiceRow(listOf("DATE", "TIME", "END_TIME", "DURATION", "LOCATION", "ONLINE_OR_LOCATION", "TITLE"), semantic) { semantic = it }
                TextButton(onClick = {
                    val r = GuidedRuleFactory.extractor(c, "field_$i", SemanticField.valueOf(semantic))
                    rules = rules.filterNot { it.key == r.key } + r
                }) { Text(tr("Feld hinzufügen", "Add field")) }
            }
            EditField(tr("Annahmevorlage, leer = global", "Acceptance template, empty = global"), acceptOverride) { acceptOverride = it }
            EditField(tr("Absagevorlage, leer = global", "Decline template, empty = global"), declineOverride) { declineOverride = it }
            Text("${rules.size} " + tr("Regeln", "rules"))
            OutlinedButton(enabled = rules.isNotEmpty(), onClick = {
                val p = ExtractionProfile(id = "preview", name = name, extractors = rules, acceptTemplate = acceptOverride.takeIf { it.isNotBlank() }, declineTemplate = declineOverride.takeIf { it.isNotBlank() })
                val r = KalPlanExtractionPipeline().extract(input, p)
                preview = r.fields.values.joinToString("\n") { "${it.semantic}: ${it.value}" } + "\n" + r.issues.joinToString { it.code }
            }) { Text(tr("Vorschau testen", "Test preview")) }
            Text(preview, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = rules.isNotEmpty() && name.isNotBlank(), onClick = {
        val p = ExtractionProfile(id = UUID.randomUUID().toString(), name = name, extractors = rules)
        if (ProfileValidator().validate(p).isEmpty()) onSave(p)
    }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}
