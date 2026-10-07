package cc.stkmn.kalplan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.BuildConfig
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.extraction.*
import cc.stkmn.kalplan.infrastructure.calendar.*
import cc.stkmn.kalplan.infrastructure.mail.*
import cc.stkmn.kalplan.infrastructure.reply.ReplyPolicy
import cc.stkmn.kalplan.infrastructure.sync.SyncScheduler
import cc.stkmn.kalplan.infrastructure.routing.BoundedHttps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

@Composable fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(100_000)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
}
@Composable fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(4096)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
}
@Composable fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
@Composable fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { item -> FilterChip(selected = selected == item, onClick = { onSelect(item) }, label = { Text(item) }) }
    }
}
@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }

@Composable
fun SettingsScreen(state: AppData, repository: AppRepository, busy: Boolean, onRun: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    var noticesOpen by remember { mutableStateOf(false) }
    var accountEdit by remember { mutableStateOf<MailAccount?>(null) }
    var newAccount by remember { mutableStateOf(false) }
    var profileJson by rememberSaveable { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf("") }
    var draft by remember(state.settings) { mutableStateOf(state.settings) }
    var duration by remember(state.settings) { mutableStateOf(draft.defaultDuration.toString()) }
    var sync by remember(state.settings) { mutableStateOf(draft.syncMinutes.toString()) }
    var stale by remember(state.settings) { mutableStateOf(draft.staleHours.toString()) }
    var before by remember(state.settings) { mutableStateOf(draft.beforeBuffer.toString()) }
    var after by remember(state.settings) { mutableStateOf(draft.afterBuffer.toString()) }
    var threshold by remember(state.settings) { mutableStateOf(draft.originThreshold.toString()) }
    var limit by remember(state.settings) { mutableStateOf(draft.routingDailyLimit.toString()) }
    var workRate by remember(state.settings) { mutableStateOf((draft.value.workCentsPerHour / 100.0).toString()) }
    var travelRate by remember(state.settings) { mutableStateOf((draft.value.travelCentsPerHour / 100.0).toString()) }
    var kmRate by remember(state.settings) { mutableStateOf((draft.value.centsPerKm / 100.0).toString()) }
    var flatRate by remember(state.settings) { mutableStateOf((draft.value.flatCents / 100.0).toString()) }
    var step by remember(state.settings) { mutableStateOf(draft.value.billingStepMinutes.toString()) }
    var labelJson by remember(state.settings) { mutableStateOf(kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }.encodeToString(kotlinx.serialization.builtins.ListSerializer(LabelPolicy.serializer()), draft.labels)) }
    var attachmentJson by remember(state.settings) { mutableStateOf(kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }.encodeToString(kotlinx.serialization.builtins.ListSerializer(AttachmentRule.serializer()), draft.attachmentRules)) }
    var feedback by remember { mutableStateOf("") }
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    var taps by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    var calendarList by remember { mutableStateOf(emptyList<cc.stkmn.kalplan.domain.port.CalendarRef>()) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onRun { calendarList = AndroidCalendarReader(context).calendars() }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) feedback = "Kalenderschreiben erlaubt / Calendar writing allowed" }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { runCatching { calendarList = AndroidCalendarReader(context).calendars() } }
    var exportJson by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) onRun { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson.toByteArray()) } ?: error("Cannot export") } } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onRun {
        val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val n = stream.read(buffer); if (n < 0) break; require(out.size() + n <= 1_000_000); out.write(buffer, 0, n) }
            out.toString("UTF-8")
        } ?: error("Cannot import") }
        profileJson = text
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Einstellungen", "Settings"), style = MaterialTheme.typography.headlineMedium)
        Text(tr("Konten, Kalender und Planung bleiben unter deiner Kontrolle.", "You control accounts, calendars and planning."))
        SectionTitle(tr("Mailkonten", "Mail accounts"))
        state.accounts.forEach { a ->
            ListItem(headlineContent = { Text(a.name) }, supportingContent = { Text(a.address + "\n" + a.folders.joinToString()) }, modifier = Modifier.clickable { accountEdit = a })
            ToggleRow(tr("Aktiv", "Enabled"), a.enabled) { enabled -> onRun { repository.update { it.copy(accounts = it.accounts.map { old -> if (old.id == a.id) old.copy(enabled = enabled) else old }) } } }
        }
        OutlinedButton(enabled = !busy, onClick = { newAccount = true }) { Text(tr("Konto hinzufügen", "Add account")) }
        Text(tr("Passwort/App-Passwort oder OAuth mit registrierter nativer Client-ID. Keine fest eingebauten Provider-Schlüssel.", "Password/app password or OAuth with a registered native client ID. No embedded provider credentials."), style = MaterialTheme.typography.bodySmall)
        SectionTitle(tr("Extraktionsprofile", "Extraction profiles"))
        state.profiles.forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { profileJson = ExtractionProfileCodec().encode(p) }) { Text(p.name) }
                TextButton(onClick = { exportJson = ExtractionProfileCodec().encode(p); export.launch("KalPlan-${p.id}.json") }) { Text(tr("Export", "Export")) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text(tr("Import", "Import")) }
            TextButton(onClick = { profileJson = "" }) { Text(tr("JSON bearbeiten", "Edit JSON")) }
        }
        SectionTitle(tr("Kalender und Datenschutz", "Calendars and privacy"))
        OutlinedButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text(tr("Kalenderzugriff erlauben / laden", "Allow / load calendars")) }
        calendarList.forEach { calendar ->
            val p = state.calendars.firstOrNull { it.id == calendar.id } ?: CalendarPrivacy(calendar.id)
            fun update(next: CalendarPrivacy) { onRun { repository.update { it.copy(calendars = it.calendars.filterNot { c -> c.id == next.id } + next) } } }
            Card { Column(Modifier.padding(12.dp)) {
                ToggleRow(calendar.displayName, p.included) { update(p.copy(included = it)) }
                if (p.included) {
                    ToggleRow(tr("Titel anzeigen", "Show title"), p.showTitle) { update(p.copy(showTitle = it)) }
                    ToggleRow(tr("Ort anzeigen", "Show location"), p.showLocation) { update(p.copy(showLocation = it)) }
                    ToggleRow(tr("Beschreibung anzeigen", "Show description"), p.showDescription) { update(p.copy(showDescription = it)) }
                    ToggleRow(tr("Verborgenen Ort für manuelle Fahrprüfung nutzen", "Use hidden location for manual travel check"), p.useHiddenLocationForRouting) { update(p.copy(useHiddenLocationForRouting = it)) }
                }
                FilterChip(selected = draft.reservationCalendarId == calendar.id, onClick = { draft = draft.copy(reservationCalendarId = if (draft.reservationCalendarId == calendar.id) "" else calendar.id) }, label = { Text(tr("Reservierungsziel", "Reservation target")) })
            } }
        }
        OutlinedButton(onClick = { writePermission.launch(Manifest.permission.WRITE_CALENDAR) }) { Text(tr("Schreibrecht für Reservierungen erlauben", "Allow reservation writing")) }
        OutlinedButton(enabled = !busy && !state.settings.debug, onClick = { onRun {
            val id = AndroidReservationWriter(context).createLocalCalendar()
            repository.update { it.copy(settings = it.settings.copy(reservationCalendarId = id), calendars = it.calendars + CalendarPrivacy(id, included = true, showTitle = true)) }
            calendarList = AndroidCalendarReader(context).calendars()
        } }) { Text(tr("KalPlan-Reservierungskalender anlegen", "Create KalPlan reservation calendar")) }
        Text(tr("Bei nicht unterstütztem Provider wähle einen bestehenden Kalender als Ziel.", "If the provider does not support creation, select an existing calendar."), style = MaterialTheme.typography.bodySmall)
        SectionTitle(tr("Planung", "Planning"))
        EditField(tr("Standarddauer, Minuten", "Default duration, minutes"), duration) { duration = it }
        EditField(tr("Relevanz, Stunden", "Relevance, hours"), stale) { stale = it }
        EditField(tr("Puffer davor, Minuten", "Buffer before, minutes"), before) { before = it }
        EditField(tr("Puffer danach, Minuten", "Buffer after, minutes"), after) { after = it }
        EditField(tr("Vorheriger Termin als Startort bis, Minuten", "Previous event origin threshold, minutes"), threshold) { threshold = it }
        EditField(tr("Standard-Ort, Name", "Default place, name"), draft.originName) { draft = draft.copy(originName = it) }
        EditField(tr("Standard-Ort, Adresse", "Default place, address"), draft.originAddress) { draft = draft.copy(originAddress = it) }
        SectionTitle(tr("Synchronisierung", "Sync"))
        EditField(tr("Intervall, Minuten. 0 = nur manuell", "Interval, minutes. 0 = manual only"), sync) { sync = it }
        Text(tr("5 bis 14 Minuten: Best-Effort. Android darf Läufe verzögern.", "5 to 14 minutes: best effort. Android may delay execution."), style = MaterialTheme.typography.bodySmall)
        ToggleRow(tr("Wochenende pausieren", "Pause weekends"), draft.pauseWeekends) { draft = draft.copy(pauseWeekends = it) }
        ChoiceRow((1..7).map { it.toString() }, "") { day -> val d = day.toInt(); draft = draft.copy(pausedWeekdays = if (d in draft.pausedWeekdays) draft.pausedWeekdays - d else draft.pausedWeekdays + d) }
        Text(tr("Pausierte Wochentage (1 = Montag): ", "Paused weekdays (1 = Monday): ") + draft.pausedWeekdays.sorted().joinToString())
        EditField(tr("Urlaub von (JJJJ-MM-TT)", "Vacation from (YYYY-MM-DD)"), draft.pauseFrom) { draft = draft.copy(pauseFrom = it) }
        EditField(tr("Urlaub bis (JJJJ-MM-TT)", "Vacation until (YYYY-MM-DD)"), draft.pauseUntil) { draft = draft.copy(pauseUntil = it) }
        state.lastSyncMillis?.let { Text(tr("Letzter erfolgreicher Sync: ", "Last successful sync: ") + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())) }
        SectionTitle(tr("Antworten und Reservierungen", "Replies and reservations"))
        EditField(tr("Annahmevorlage", "Acceptance template"), draft.acceptTemplate) { draft = draft.copy(acceptTemplate = it) }
        EditField(tr("Absagevorlage", "Decline template"), draft.declineTemplate) { draft = draft.copy(declineTemplate = it) }
        Text("{date}, {time}, {subject}, {sender}", style = MaterialTheme.typography.bodySmall)
        Text(tr("Kalenderbeschreibung: FULL überträgt den ganzen Mailtext in deinen Kalender.", "Calendar description: FULL copies the entire email text into your calendar."))
        ChoiceRow(listOf("NONE", "FULL", "EXCLUDE", "EXCERPTS", "TEMPLATE"), draft.reservationDescription) { draft = draft.copy(reservationDescription = it) }
        if (draft.reservationDescription == "TEMPLATE") EditField(tr("Beschreibungsvorlage", "Description template"), draft.reservationTemplate) { draft = draft.copy(reservationTemplate = it) }
        if (draft.reservationDescription == "EXCLUDE") EditField(tr("Ausgeschlossene Textblöcke, getrennt durch ||", "Excluded text blocks, separated by ||"), draft.reservationExcludeBlocks.joinToString("||")) { draft = draft.copy(reservationExcludeBlocks = it.split("||")) }
        if (draft.reservationDescription == "EXCERPTS") EditField(tr("Textausschnitte, getrennt durch ||", "Excerpts, separated by ||"), draft.reservationExcerpts.joinToString("||")) { draft = draft.copy(reservationExcerpts = it.split("||")) }
        SectionTitle(tr("Labels und Prioritätsregeln", "Labels and priority rules"))
        Text(tr("Regeln unterstützen Schlagworte, Absenderfilter, Punkte, Dauer-Overrides und Wechselwirkungen mit kurzen Terminen.", "Rules support keywords, sender filters, scores, duration overrides and interactions with short appointments."))
        EditField("JSON", labelJson) { labelJson = it }
        SectionTitle(tr("Auftragswert", "Order value"))
        ToggleRow(tr("Wertschätzung aktivieren", "Enable value estimates"), draft.value.enabled) { draft = draft.copy(value = draft.value.copy(enabled = it)) }
        if (draft.value.enabled) {
            EditField(tr("Arbeitsstundensatz, EUR", "Hourly work rate, EUR"), workRate) { workRate = it }
            EditField(tr("Fahrtstundensatz, EUR", "Hourly travel rate, EUR"), travelRate) { travelRate = it }
            EditField(tr("Kilometersatz, EUR", "Per kilometer rate, EUR"), kmRate) { kmRate = it }
            EditField(tr("Pauschale, EUR", "Flat fee, EUR"), flatRate) { flatRate = it }
            EditField(tr("Abrechnungsschritt, Minuten", "Billing increment, minutes"), step) { step = it }
            ToggleRow(tr("Aufrunden (sonst kaufmännisch)", "Round up (otherwise nearest)"), draft.value.roundUp) { draft = draft.copy(value = draft.value.copy(roundUp = it)) }
            ToggleRow(tr("Hin- und Rückfahrt abrechnen", "Charge round trip"), draft.value.roundTrip) { draft = draft.copy(value = draft.value.copy(roundTrip = it)) }
        }
        SectionTitle(tr("Darstellung und Anhänge", "Appearance and attachments"))
        ChoiceRow(listOf("MATERIAL_YOU", "KALPLAN", "NEUTRAL_BUSINESS", "TURQUOISE", "HIGH_CONTRAST"), draft.theme) { draft = draft.copy(theme = it) }
        EditField(tr("Eigene Primärfarbe, RRGGBB oder leer", "Custom primary color, RRGGBB or empty"), draft.primaryHex) { draft = draft.copy(primaryHex = it.removePrefix("#")) }
        ChoiceRow(listOf("IGNORE", "RELEVANT", "ALL"), draft.attachments) { draft = draft.copy(attachments = it) }
        Text(tr("Anhangregeln: erste passende Regel gilt. MIME-Präfix, Endung, Name, Größe und Inline-Status sind kombinierbar.", "Attachment rules: first match wins. Combine MIME prefix, extension, name, size and inline status."))
        EditField(tr("Anhangregeln, JSON", "Attachment rules, JSON"), attachmentJson) { attachmentJson = it }
        EditField(tr("Routing-Tageslimit je Anbieter", "Routing daily limit per provider"), limit) { limit = it }
        if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Benachrichtigungen erlauben", "Allow notifications")) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            runCatching {
                val interval = sync.toInt(); require(interval == 0 || interval in 5..1440)
                val d = duration.toInt(); require(d in 1..10080)
                val s = stale.toInt(); require(s in 1..8760)
                val b = before.toInt(); val a = after.toInt(); val t = threshold.toInt(); val l = limit.toInt()
                require(b in 0..1440 && a in 0..1440 && t in 0..1440 && l in 0..1000)
                require(draft.primaryHex.isBlank() || draft.primaryHex.matches(Regex("[0-9a-fA-F]{6}")))
                if (draft.pauseFrom.isNotBlank() || draft.pauseUntil.isNotBlank()) require(LocalDate.parse(draft.pauseUntil) >= LocalDate.parse(draft.pauseFrom))
                fun cents(value: String): Long = java.math.BigDecimal(value.replace(',', '.')).multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact().also { require(it in 0..100_000_000) }
                val increment = step.toInt(); require(increment in 1..1440)
                val labels = kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LabelPolicy.serializer()), labelJson)
                require(labels.size <= 100 && labels.all { it.name.isNotBlank() && (it.durationMinutes == null || it.durationMinutes in 1..10080) && it.score in -100..100 && it.shortScore in -100..100 })
                val attachmentRules = kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AttachmentRule.serializer()), attachmentJson)
                require(attachmentRules.size <= 100 && attachmentRules.all { (it.minBytes == null || it.minBytes >= 0) && (it.maxBytes == null || it.maxBytes >= 0) && (it.minBytes == null || it.maxBytes == null || it.minBytes <= it.maxBytes) })
                val next = draft.copy(attachmentRules = attachmentRules, syncMinutes = interval, defaultDuration = d, staleHours = s, beforeBuffer = b, afterBuffer = a, originThreshold = t, routingDailyLimit = l, labels = labels,
                    value = draft.value.copy(workCentsPerHour = cents(workRate), travelCentsPerHour = cents(travelRate), centsPerKm = cents(kmRate), flatCents = cents(flatRate), billingStepMinutes = increment))
                onRun { repository.update { it.copy(settings = next) }; SyncScheduler.configure(context, next.syncMinutes); feedback = "Gespeichert / Saved" }
            }.onFailure { feedback = "Eingaben prüfen / Check inputs" }
        }) { Text(tr("Einstellungen speichern", "Save settings")) }
        Text(feedback, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("Vibecoded with ❤️", modifier = Modifier.clickable {
            val now = android.os.SystemClock.elapsedRealtime()
            taps = if (now - lastTap < 1200) taps + 1 else 1; lastTap = now
            if (taps >= 5) { debugOpen = true; taps = 0 }
        })
        TextButton(onClick = { noticesOpen = true }) { Text(tr("Open-Source-Lizenzen", "Open-source licenses")) }
        Text("Version ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " (Debug APK)" else "")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/3115a083/KalPlan"))) }) { Text("GitHub") }
            TextButton(enabled = !busy, onClick = { onRun {
                feedback = withContext(Dispatchers.IO) {
                    val release = BoundedHttps.json("https://api.github.com/repos/3115a083/KalPlan/releases/latest")
                    require(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
                    "Letzte stabile Version / Latest stable version: " + release.getString("tag_name").take(40)
                }
            } }) { Text(tr("Update prüfen", "Check update")) }
        }
        if (debugOpen) {
            SectionTitle("Debug")
            ToggleRow(tr("Debugmodus", "Debug mode"), state.settings.debug) { enabled -> onRun { repository.update { it.copy(settings = it.settings.copy(debug = enabled)) } } }
            var testAddress by remember(state.settings.debugTestAddress) { mutableStateOf(state.settings.debugTestAddress) }
            EditField(tr("Testadresse", "Test address"), testAddress) { testAddress = it }
            OutlinedButton(onClick = { onRun { ReplyPolicy.address(testAddress); repository.update { it.copy(settings = it.settings.copy(debugTestAddress = testAddress)) } } }) { Text(tr("Testadresse speichern", "Save test address")) }
            ToggleRow(tr("Testmails tatsächlich an Testadresse senden", "Actually send test mail to test address"), state.settings.debugSendToTest) { enabled -> onRun { if (enabled) ReplyPolicy.address(state.settings.debugTestAddress); repository.update { it.copy(settings = it.settings.copy(debugSendToTest = enabled)) } } }
            Text(tr("Kalenderwrites bleiben im Debugmodus simuliert.", "Calendar writes stay simulated in debug mode."))
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.diagnostics.joinToString("\n"))) }) { Text(tr("Redigierte Diagnose kopieren", "Copy redacted diagnostics")) }
            SelectionContainer { Text(state.diagnostics.takeLast(20).joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(40.dp))
    }
    if (noticesOpen) {
        val notice = remember { context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name -> name + "\n" + context.assets.open("licenses/$name").bufferedReader().use { it.readText() } } }
        AlertDialog(onDismissRequest = { noticesOpen = false }, title = { Text(tr("Open-Source-Lizenzen", "Open-source licenses")) },
            text = { SelectionContainer { Text(notice, Modifier.verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { noticesOpen = false }) { Text(tr("Schließen", "Close")) } })
    }
    if (newAccount || accountEdit != null) AccountDialog(accountEdit, state, repository, busy, onRun,
        onDismiss = { newAccount = false; accountEdit = null })
    profileJson?.let { json ->
        AlertDialog(onDismissRequest = { profileJson = null }, title = { Text(tr("Profil validieren", "Validate profile")) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { EditField("JSON", json) { profileJson = it }; Text(profileError, color = MaterialTheme.colorScheme.error) }
        }, confirmButton = { TextButton(onClick = {
            val decoded = ExtractionProfileCodec().decode(json)
            if (decoded.isValid) { val p = decoded.profile!!; profileJson = null; onRun { repository.update { it.copy(profiles = it.profiles.filterNot { old -> old.id == p.id } + p) } } }
            else profileError = decoded.validationErrors.joinToString("\n") { it.message }
        }) { Text(tr("Validieren und speichern", "Validate and save")) } }, dismissButton = { TextButton(onClick = { profileJson = null }) { Text(tr("Abbrechen", "Cancel")) } })
    }
}

@Composable
private fun AccountDialog(existing: MailAccount?, state: AppData, repository: AppRepository, busy: Boolean, onRun: (suspend () -> Unit) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    var authMode by rememberSaveable { mutableStateOf(existing?.authMode ?: "PASSWORD") }
    var clientId by rememberSaveable { mutableStateOf(existing?.oauthClientId.orEmpty()) }
    var authorizationEndpoint by rememberSaveable { mutableStateOf(existing?.oauthAuthorizationEndpoint.orEmpty()) }
    var tokenEndpoint by rememberSaveable { mutableStateOf(existing?.oauthTokenEndpoint.orEmpty()) }
    var oauthScope by rememberSaveable { mutableStateOf(existing?.oauthScope.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(existing?.address.orEmpty()) }
    var imap by rememberSaveable { mutableStateOf(existing?.imapHost.orEmpty()) }
    var smtp by rememberSaveable { mutableStateOf(existing?.smtpHost.orEmpty()) }
    var imapPort by rememberSaveable { mutableStateOf((existing?.imapPort ?: 993).toString()) }
    var smtpPort by rememberSaveable { mutableStateOf((existing?.smtpPort ?: 465).toString()) }
    var incomingStartTls by rememberSaveable { mutableStateOf(existing?.imapStartTls ?: false) }
    var outgoingStartTls by rememberSaveable { mutableStateOf(existing?.smtpStartTls ?: false) }
    var password by remember { mutableStateOf("") }
    var outgoingPassword by remember { mutableStateOf("") }
    var folders by rememberSaveable { mutableStateOf(existing?.folders?.joinToString("\n") ?: "INBOX") }
    var signature by rememberSaveable { mutableStateOf(existing?.signature.orEmpty()) }
    var folderProfiles by remember { mutableStateOf(existing?.folderProfiles ?: emptyMap()) }
    var feedback by remember { mutableStateOf("") }
    var serverFolders by remember { mutableStateOf(emptyList<String>()) }
    fun account(): MailAccount {
        ReplyPolicy.address(address)
        require(username.isNotBlank() && name.isNotBlank())
        require(imap.isNotBlank() && smtp.isNotBlank() && !imap.any { it.isWhitespace() || it == '/' } && !smtp.any { it.isWhitespace() || it == '/' })
        require(imapPort.toInt() in 1..65535 && smtpPort.toInt() in 1..65535)
        require(authMode == "XOAUTH2" || existing != null || password.isNotBlank())
        if (authMode == "XOAUTH2") {
            require(clientId.isNotBlank() && oauthScope.isNotBlank())
            cc.stkmn.kalplan.infrastructure.oauth.OAuthAccess.httpsEndpoint(authorizationEndpoint)
            cc.stkmn.kalplan.infrastructure.oauth.OAuthAccess.httpsEndpoint(tokenEndpoint)
        }
        val selected = folders.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
        require(selected.isNotEmpty() && selected.size <= 20)
        return MailAccount(id, name, username, address, imap, imapPort.toInt(), incomingStartTls, smtp, smtpPort.toInt(), outgoingStartTls, selected, signature, existing?.enabled ?: true, folderProfiles, authMode, clientId, authorizationEndpoint, tokenEndpoint, oauthScope)
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(tr("Mailkonto", "Mail account")) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(tr("Name", "Name"), name) { name = it }
            EditField(tr("Anmelde-Benutzername", "Login username"), username) { username = it }
            EditField(tr("Absenderadresse für Antworten", "From address for replies"), address) { address = it }
            EditField("IMAP Host", imap) { imap = it }
            EditField("IMAP Port", imapPort) { imapPort = it }
            ToggleRow("IMAP STARTTLS", incomingStartTls) { incomingStartTls = it }
            EditField("SMTP Host", smtp) { smtp = it }
            EditField("SMTP Port", smtpPort) { smtpPort = it }
            ToggleRow("SMTP STARTTLS", outgoingStartTls) { outgoingStartTls = it }
            Text(tr("Ohne STARTTLS gilt implizites TLS. Zertifikate und Hostnamen werden immer geprüft.", "Without STARTTLS, implicit TLS is used. Certificates and hostnames are always verified."), style = MaterialTheme.typography.bodySmall)
            ChoiceRow(listOf("PASSWORD", "XOAUTH2"), authMode) { authMode = it }
            if (authMode == "XOAUTH2") {
                Text(tr("Erweiterte Einrichtung: Registriere KalPlan als nativen öffentlichen Client bei deinem Anbieter. Redirect: ", "Advanced setup: register KalPlan as a native public client with your provider. Redirect: ") + cc.stkmn.kalplan.infrastructure.oauth.OAuthAccess.REDIRECT)
                EditField("Client-ID", clientId) { clientId = it }
                EditField("Authorization endpoint (HTTPS)", authorizationEndpoint) { authorizationEndpoint = it }
                EditField("Token endpoint (HTTPS)", tokenEndpoint) { tokenEndpoint = it }
                EditField("Scopes", oauthScope) { oauthScope = it }
                OutlinedButton(onClick = {
                    authorizationEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
                    tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
                    oauthScope = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send"
                }) { Text(tr("Microsoft-Endpunkte verwenden", "Use Microsoft endpoints")) }
                Button(enabled = !busy, onClick = {
                    runCatching { val a = account(); onRun {
                        repository.saveAccount(a, "", "")
                        context.startActivity(Intent(context, cc.stkmn.kalplan.infrastructure.oauth.OAuthActivity::class.java).putExtra("account_id", id))
                    } }.onFailure { feedback = "OAuth-Konfiguration prüfen / Check OAuth setup" }
                }) { Text(tr("Speichern und mit OAuth anmelden", "Save and sign in with OAuth")) }
            } else {
                SecretField(tr("IMAP-Passwort, leer = beibehalten", "IMAP password, empty = keep"), password) { password = it }
                SecretField(tr("SMTP-Passwort, leer = IMAP-Passwort / beibehalten", "SMTP password, empty = IMAP password / keep"), outgoingPassword) { outgoingPassword = it }
            }
            EditField(tr("Überwachte Ordner, ein Pfad pro Zeile", "Watched folders, one path per line"), folders) { folders = it }
            OutlinedButton(enabled = !busy, onClick = {
                runCatching { val a = account(); onRun {
                    repository.saveAccount(a, password, outgoingPassword.ifBlank { password })
                    val p = AccountProviders(repository)
                    serverFolders = AngusMailReader(p, p).listFolders(id).map { it.path }
                    feedback = "Verbunden / Connected"
                } }.onFailure { feedback = "Eingaben prüfen / Check inputs" }
            }) { Text(tr("Speichern und Ordner abrufen", "Save and fetch folders")) }
            serverFolders.forEach { folder -> FilterChip(selected = folder in folders.lines(), onClick = { val current = folders.lines().filter { it.isNotBlank() }; folders = (if (folder in current) current - folder else current + folder).joinToString("\n") }, label = { Text(folder) }) }
            folders.lines().filter { it.isNotBlank() }.forEach { folder ->
                Text(folder + " · " + tr("Profil", "Profile"), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf("HEURISTIC") + state.profiles.map { it.id }, folderProfiles[folder] ?: "HEURISTIC") { p -> folderProfiles = if (p == "HEURISTIC") folderProfiles - folder else folderProfiles + (folder to p) }
            }
            EditField(tr("Signatur", "Signature"), signature) { signature = it }
            Text(feedback, color = MaterialTheme.colorScheme.primary)
            if (existing != null) TextButton(enabled = !busy, onClick = { onRun { repository.removeAccount(id); onDismiss() } }) { Text(tr("Konto lokal entfernen. Mails bleiben im Verlauf.", "Remove local account. Requests stay in history.")) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        runCatching { val a = account(); onRun { repository.saveAccount(a, password, outgoingPassword.ifBlank { password }); onDismiss() } }.onFailure { feedback = "Eingaben prüfen / Check inputs" }
    }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Schließen", "Close")) } })
}
