package cc.stkmn.kalplan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.BuildConfig
import cc.stkmn.kalplan.R
import cc.stkmn.kalplan.application.*
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.infrastructure.reply.ReplyPolicy
import cc.stkmn.kalplan.infrastructure.sync.SyncScheduler
import cc.stkmn.kalplan.infrastructure.widget.RequestSurfaces
import cc.stkmn.kalplan.ui.theme.*
import kotlinx.coroutines.launch
import java.time.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalPlanApp(deepLink: Pair<String?, String?> = null to null) {
    val context = LocalContext.current
    val repository = remember { AppRepository.get(context) }
    val state by repository.data.collectAsState()
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf("REQUESTS") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var replyAction by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val planner = remember { Planner(context, repository) }
    fun run(action: suspend () -> Unit) {
        if (busy || !ready) return
        scope.launch {
            busy = true
            try { action(); RequestSurfaces.updateWidgets(context) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.javaClass.simpleName + ": " + (if (e is IllegalArgumentException) e.message?.take(120).orEmpty() else "Die Aktion ist fehlgeschlagen / Action failed") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        try { repository.load(); ready = true; SyncScheduler.configure(context, repository.data.value.settings.syncMinutes); RequestSurfaces.updateWidgets(context) }
        catch (_: Exception) { error = "Lokaler Datenspeicher konnte nicht geöffnet werden. Vorhandene Daten bleiben erhalten. / Cannot open local storage. Existing data is preserved." }
    }
    LaunchedEffect(deepLink, ready) {
        if (ready && deepLink.first != null && state.requests.any { it.id == deepLink.first }) {
            selected = deepLink.first; replyAction = deepLink.second?.takeIf { it == "accept" || it == "decline" }
        }
    }
    val theme = runCatching { ThemeChoice.valueOf(state.settings.theme) }.getOrDefault(ThemeChoice.KALPLAN)
    KalPlanTheme(choice = theme, primaryHex = state.settings.primaryHex) {
        val request = state.requests.firstOrNull { it.id == selected }
        BackHandler(selected != null || section == "SWIPE") { if (replyAction != null) replyAction = null else { selected = null; section = "REQUESTS" } }
        Scaffold(
            topBar = {
                TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(36.dp))
                    Text(if (selected == null) "KalPlan" else tr("Terminanfrage", "Appointment request"), fontWeight = FontWeight.Bold)
                } }, navigationIcon = {
                    if (selected != null) IconButton(onClick = { replyAction = null; selected = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Zurück", "Back")) }
                }, actions = {
                    if (selected == null) {
                        IconButton(enabled = ready && !busy, onClick = { SyncScheduler.manual(context); Toast.makeText(context, "Sync angefordert / Sync requested", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Outlined.Sync, tr("Synchronisieren", "Sync")) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("Menü", "Menu")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("Swipe-Ansicht", "Swipe view")) }, onClick = { section = "SWIPE"; menu = false })
                            DropdownMenuItem(text = { Text(tr("Text / E-Mail importieren", "Import text / email")) }, onClick = { importing = true; menu = false })
                            DropdownMenuItem(text = { Text(if (showHistory) tr("Offene Anfragen", "Pending requests") else tr("Verlauf anzeigen", "Show history")) }, onClick = { showHistory = !showHistory; menu = false })
                        }
                    }
                })
            },
            bottomBar = {
                if (selected == null && section != "SWIPE") NavigationBar {
                    listOf("REQUESTS", "CALENDAR", "UNCLEAR", "SETTINGS").forEach { item ->
                        NavigationBarItem(selected = section == item, onClick = { section = item },
                            icon = { Icon(when (item) { "CALENDAR" -> Icons.Outlined.CalendarMonth; "UNCLEAR" -> Icons.Outlined.HelpOutline; "SETTINGS" -> Icons.Outlined.Settings; else -> Icons.Outlined.Inbox }, null) },
                            label = { Text(when (item) { "CALENDAR" -> tr("Kalender", "Calendar"); "UNCLEAR" -> tr("Unklar", "Unclear"); "SETTINGS" -> tr("Einstellungen", "Settings"); else -> tr("Anfragen", "Requests") }) })
                    }
                }
            },
            floatingActionButton = {
                if (selected == null && section in setOf("REQUESTS", "UNCLEAR", "CALENDAR")) FloatingActionButton(onClick = { importing = true }) { Icon(Icons.Outlined.Add, tr("Anfrage hinzufügen", "Add request")) }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy || !ready && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (ready) {
                    if (request != null) {
                        RequestDetail(request, state, repository, planner, busy, replyAction,
                            onAction = { replyAction = it }, onRun = ::run, onClose = { selected = null; replyAction = null })
                    } else when (section) {
                        "SETTINGS" -> SettingsScreen(state, repository, busy, ::run)
                        "CALENDAR" -> CalendarScreen(state, repository, planner, onOpen = { selected = it })
                        "SWIPE" -> SwipeScreen(state, planner, onOpen = { selected = it }, onAction = { id, action -> selected = id; replyAction = action },
                            onLater = { id -> run { repository.request(id) { it.copy(status = "LATER") } } }, onUnclear = { id -> run { repository.request(id) { it.copy(status = "UNCLEAR", unclear = true) } } }, onClose = { section = "REQUESTS" })
                        else -> {
                            OutlinedTextField(search, { search = it }, label = { Text(tr("Suchen", "Search")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true)
                            val requests = state.requests.filter {
                                (if (showHistory) !it.pending else it.pending) && (section != "UNCLEAR" || it.unclear) &&
                                    (search.isBlank() || (it.subject + it.sender + it.labels.joinToString()).contains(search, true))
                            }.sortedByDescending { PlanningPolicy.priority(it, state.settings).score }
                            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { Text(if (showHistory) tr("Verlauf", "History") else if (section == "UNCLEAR") tr("Angaben prüfen", "Review details") else tr("Deine Anfragen", "Your requests"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                                if (requests.isEmpty()) item {
                                    Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(tr("Hier ist noch Platz für deine Planung.", "Ready for your next appointment."), style = MaterialTheme.typography.titleMedium)
                                        Text(tr("Richte ein Mailkonto ein oder importiere einen Text. Mit Beispieldaten kannst du die Ansichten ausprobieren.", "Set up an email account or import text. Sample data lets you explore the screens."))
                                        Button(onClick = { section = "SETTINGS" }) { Text(tr("Einrichten", "Set up")) }
                                        OutlinedButton(onClick = { run { addSamples(repository) } }) { Text(tr("Beispiele laden", "Load samples")) }
                                    } }
                                }
                                items(requests, key = { it.id }) { r -> RequestCard(r, state.settings, planner, onClick = { selected = r.id }) }
                            }
                        }
                    }
                }
            }
        }
        if (importing && ready) ImportRequestDialog(onDismiss = { importing = false }, onSave = { sender, subject, body ->
            importing = false
            run {
                val r = RequestFactory.create(UUID.randomUUID().toString(), sender, subject, body, System.currentTimeMillis(), repository.data.value)
                repository.update { it.copy(requests = it.requests + r) }
                selected = r.id
            }
        })
        error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text(tr("Aktion prüfen", "Check action")) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
    }
}

@Composable
fun StatusPill(status: String) {
    val color = when(status) { "FEASIBLE" -> Color(0xFF12653B); "CONFLICT" -> Color(0xFFAC2445); "POSSIBLE" -> Color(0xFF835500); else -> MaterialTheme.colorScheme.primary }
    Surface(color = color.copy(alpha = 0.12f), contentColor = if (isSystemInDarkTheme()) when(status) { "FEASIBLE" -> Color(0xFF7AE4AF); "CONFLICT" -> Color(0xFFFF9CB2); "POSSIBLE" -> Color(0xFFFFD17D); else -> MaterialTheme.colorScheme.primary } else color,
        shape = RoundedCornerShape(10.dp)) { Text(statusText(status), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
}
@Composable
fun RequestCard(request: StoredRequest, settings: Settings, planner: Planner, onClick: () -> Unit) {
    var assessment by remember(request.id) { mutableStateOf<Assessment?>(null) }
    LaunchedEffect(request, settings.beforeBuffer, settings.afterBuffer) { assessment = runCatching { planner.assess(request) }.getOrNull() }
    val priority = PlanningPolicy.priority(request, settings)
    val status = if (request.pending) if (request.unclear) "UNKNOWN" else assessment?.status ?: "UNKNOWN" else request.status
    val border = when(status) { "FEASIBLE" -> Color(0xFF40BD88); "CONFLICT" -> Color(0xFFEE5378); "POSSIBLE" -> Color(0xFFF4B54B); else -> MaterialTheme.colorScheme.primary }
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(border))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("P${priority.rank}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    StatusPill(status)
                }
                Text(appointmentTime(request.candidate ?: request.candidates.firstOrNull()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(request.subject, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                if (request.labels.isNotEmpty()) Text(request.labels.joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (request.candidate?.mode == "ONLINE") tr("Online", "Online") else request.candidate?.location.orEmpty().ifBlank { request.sender }, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (settings.value.enabled && request.candidate != null) Text("≈ " + String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "%.2f €", PlanningPolicy.value(request.candidate!!, request.travelMinutes, request.distanceKm, settings.value).totalCents / 100.0), fontWeight = FontWeight.SemiBold)
                }
                if (priority.stale) Text(tr("Veraltet. Weiterhin bearbeitbar.", "Stale. Still available for review."), style = MaterialTheme.typography.labelSmall)
                if (request.demo) Text(tr("Beispiel, Versand gesperrt", "Sample, sending disabled"), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ImportRequestDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var sender by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Text importieren", "Import text")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Der Import bleibt lokal. Für Antworten per SMTP ist eine synchronisierte Quellmail nötig.", "The import stays local. SMTP replies require a synced source email."))
            OutlinedTextField(sender, { sender = it.take(320) }, label = { Text(tr("Absender", "Sender")) })
            OutlinedTextField(subject, { subject = it.take(500) }, label = { Text(tr("Betreff", "Subject")) })
            OutlinedTextField(body, { body = it.take(100_000) }, label = { Text(tr("E-Mail-Text", "Email text")) }, minLines = 6)
        }
    }, confirmButton = { TextButton(enabled = body.isNotBlank(), onClick = { onSave(sender, subject.ifBlank { "Anfrage / Request" }, body) }) { Text(tr("Analysieren", "Analyze")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

private suspend fun addSamples(repository: AppRepository) {
    val day = LocalDate.now().plusDays(1)
    val state = repository.data.value
    val requests = listOf(
        Triple("Praxis Beispiel", "Medizin", "Datum: $day\nBeginn: 14:00\nEnde: 16:00\nOrt: Musterstraße 12, 44137 Dortmund"),
        Triple("Firma Netzwerk GmbH", "LWL", "Datum: $day\nBeginn: 10:00\nEnde: 12:00\nOrt: Essen"),
        Triple("Online-Beratung", "Beratung", "Datum: $day\nBeginn: 17:00\nEnde: 18:00\nOrt oder Online: Online"),
        Triple("Neue Anfrage ohne Termin", "", "Können Sie nächste Woche einen Auftrag übernehmen? Datum und Ort folgen.")
    ).mapIndexed { i, (title, label, text) -> RequestFactory.create("demo-$i", "beispiel@example.invalid", title, "$label\n$text", System.currentTimeMillis(), state).copy(demo = true) }
    repository.update { it.copy(requests = it.requests.filterNot { old -> old.demo } + requests) }
}
