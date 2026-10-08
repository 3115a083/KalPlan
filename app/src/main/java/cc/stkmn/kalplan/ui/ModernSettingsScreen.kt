@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cc.stkmn.kalplan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.BuildConfig
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.CalendarRef
import cc.stkmn.kalplan.extraction.*
import cc.stkmn.kalplan.infrastructure.calendar.AndroidCalendarReader
import cc.stkmn.kalplan.infrastructure.reply.ReplyPolicy
import cc.stkmn.kalplan.infrastructure.routing.BoundedHttps
import cc.stkmn.kalplan.infrastructure.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import java.util.UUID

@Composable
fun SettingsScreen(state: AppData, repository: AppRepository, busy: Boolean, onRun: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    var accountEdit by remember { mutableStateOf<MailAccount?>(null) }
    var newAccount by remember { mutableStateOf(false) }
    var labelEdit by remember { mutableStateOf<LabelPolicy?>(null) }
    var newLabel by remember { mutableStateOf(false) }
    var profileEdit by remember { mutableStateOf<ExtractionProfile?>(null) }
    var newProfile by remember { mutableStateOf(false) }
    var valueOpen by remember { mutableStateOf(false) }
    var calendarEdit by remember { mutableStateOf<Pair<CalendarRef, CalendarPrivacy>?>(null) }
    var attachmentEdit by remember { mutableStateOf<Pair<Int, AttachmentRule>?>(null) }
    var newAttachment by remember { mutableStateOf(false) }
    var noticesOpen by remember { mutableStateOf(false) }
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var feedback by remember { mutableStateOf("") }
    var calendars by remember { mutableStateOf(emptyList<CalendarRef>()) }
    fun updateSettings(change: (Settings) -> Settings) = onRun { repository.update { it.copy(settings = change(it.settings)) } }
    val readCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onRun { calendars = AndroidCalendarReader(context).calendars() }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { calendars = runCatching { AndroidCalendarReader(context).calendars() }.getOrDefault(emptyList()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(tr("Einstellungen", "Settings"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(tr("Änderungen werden sofort angewendet.", "Changes apply immediately."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsGroup(Icons.Outlined.Palette, tr("Design und Sprache", "Design and language"), tr("Farben, Hell/Dunkel und Sprache", "Colors, light/dark and language"), initiallyExpanded = true) {
                Text(tr("Sprache", "Language"), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf("SYSTEM", "DE", "EN"), state.settings.language) { updateSettings { s -> s.copy(language = it) } }
                Text(tr("Helligkeit", "Brightness"), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf("SYSTEM", "LIGHT", "DARK"), state.settings.themeMode) { updateSettings { s -> s.copy(themeMode = it) } }
                Text(tr("Farbstil", "Color style"), style = MaterialTheme.typography.labelLarge)
                ThemePicker(state.settings.theme) { selected -> updateSettings { it.copy(theme = selected) } }
            }
        }
        item {
            SettingsGroup(Icons.Outlined.AlternateEmail, tr("Mailkonten und Profile", "Mail accounts and profiles"), tr("Eingang, Ordner und automatische Erkennung", "Inbox, folders and automatic extraction")) {
                state.accounts.forEach { account ->
                    SettingItem(account.name, account.address + " · " + account.folders.joinToString(), onClick = { accountEdit = account })
                }
                OutlinedButton(onClick = { newAccount = true }, enabled = !busy) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Mailkonto", "Mail account")) }
                HorizontalDivider()
                state.profiles.forEach { profile -> SettingItem(profile.name, tr("Visuelles Extraktionsprofil", "Visual extraction profile"), onClick = { profileEdit = profile }) }
                OutlinedButton(onClick = { newProfile = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Profil", "Profile")) }
            }
        }
        item {
            SettingsGroup(Icons.Outlined.Label, tr("Labels und Sortierung", "Labels and sorting"), tr("Automatische Muster, Farbe, Priorität und Preisabweichungen", "Automatic patterns, color, priority and price overrides")) {
                if (state.settings.labels.isEmpty()) Text(tr("Noch keine Labels. Lege fachliche Labels passend zu deinem Betrieb an.", "No labels yet. Create labels that fit your work."))
                state.settings.labels.forEach { label ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(parseColor(label.colorHex), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(10.dp))
                        SettingItem(label.name, labelSummary(label), Modifier.weight(1f), onClick = { labelEdit = label })
                    }
                }
                OutlinedButton(onClick = { newLabel = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Label", "Label")) }
            }
        }
        item {
            SettingsGroup(Icons.Outlined.Payments, tr("Auftragswert", "Order value"), tr("Globaler Standard, optionale Abweichungen direkt im Label", "Global default, optional overrides inside each label")) {
                ToggleRow(tr("Wert anzeigen", "Show value"), state.settings.value.enabled) { enabled -> updateSettings { it.copy(value = it.value.copy(enabled = enabled)) } }
                if (state.settings.value.enabled) {
                    Text(valueSummary(state.settings.value))
                    OutlinedButton(onClick = { valueOpen = true }) { Text(tr("Globalen Standard bearbeiten", "Edit global default")) }
                }
            }
        }
        item {
            SettingsGroup(Icons.Outlined.CalendarMonth, tr("Kalender", "Calendars"), tr("Konflikte, sichtbare Details, Fahrten und Reservierungen", "Conflicts, visible details, travel and reservations")) {
                OutlinedButton(onClick = { readCalendar.launch(Manifest.permission.READ_CALENDAR) }) { Text(tr("Kalender laden", "Load calendars")) }
                calendars.forEach { calendar ->
                    val privacy = state.calendars.firstOrNull { it.id == calendar.id } ?: CalendarPrivacy(calendar.id)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(privacy.included, onCheckedChange = { checked ->
                            if (checked) calendarEdit = calendar to privacy.copy(included = true, showTitle = true, showLocation = true, showDescription = true)
                            else onRun { repository.update { data -> data.copy(calendars = data.calendars.filterNot { it.id == calendar.id } + privacy.copy(included = false)) } }
                        })
                        SettingItem(calendar.displayName, calendarSummary(privacy), Modifier.weight(1f), enabled = privacy.included, onClick = { calendarEdit = calendar to privacy })
                    }
                }
            }
        }
        item {
            SettingsGroup(Icons.Outlined.Sync, tr("Synchronisierung", "Synchronization"), tr("Intervall, Ruhetage und tägliche Ruhezeit", "Interval, pause days and daily quiet hours")) {
                Text(tr("Intervall", "Interval"), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf("0", "5", "10", "15", "30", "60"), state.settings.syncMinutes.toString()) { value ->
                    val minutes = value.toInt(); updateSettings { it.copy(syncMinutes = minutes) }; SyncScheduler.configure(context, minutes)
                }
                Text(if (state.settings.syncMinutes == 0) tr("Nur manuell", "Manual only") else tr("Alle ${state.settings.syncMinutes} Minuten", "Every ${state.settings.syncMinutes} minutes"))
                ToggleRow(tr("Am Wochenende pausieren", "Pause on weekends"), state.settings.pauseWeekends) { enabled -> updateSettings { it.copy(pauseWeekends = enabled) } }
                Text(tr("Tägliche Ruhezeit", "Daily quiet hours"), style = MaterialTheme.typography.labelLarge)
                TimeRangeRow(state.settings.quietFrom, state.settings.quietUntil) { from, until -> updateSettings { it.copy(quietFrom = from, quietUntil = until) } }
                if (state.settings.quietFrom.isNotBlank()) Text(tr("Über Mitternacht ist möglich, z. B. 22:00 bis 06:00.", "Overnight ranges are supported, e.g. 22:00 to 06:00."), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsGroup(Icons.Outlined.Reply, tr("Antworten und Reservierungen", "Replies and reservations"), tr("Sicheres Verhalten beim Annehmen und Ablehnen", "Safe behavior when accepting and declining")) {
                ToggleRow(tr("Beim Ablehnen eine Nachricht vorbereiten", "Prepare a message when declining"), state.settings.sendDeclineReply) { enabled -> updateSettings { it.copy(sendDeclineReply = enabled) } }
                Text(tr("Aus: Ablehnen verwirft die Anfrage nur in KalPlan. Die Quellmail bleibt unverändert.", "Off: declining only dismisses the request in KalPlan. The source email stays unchanged."), style = MaterialTheme.typography.bodySmall)
                TemplateEditor(tr("Annahmevorlage", "Acceptance template"), state.settings.acceptTemplate) { text -> updateSettings { it.copy(acceptTemplate = text) } }
                if (state.settings.sendDeclineReply) TemplateEditor(tr("Absagevorlage", "Decline template"), state.settings.declineTemplate) { text -> updateSettings { it.copy(declineTemplate = text) } }
            }
        }
        item {
            SettingsGroup(Icons.Outlined.Tune, tr("Planung und Anhänge", "Planning and attachments"), tr("Dauer, Puffer, Standard-Ort und sichtbare Dateien", "Duration, buffers, default place and visible files")) {
                NumberSetting(tr("Standarddauer", "Default duration"), state.settings.defaultDuration, tr("Minuten", "minutes"), 1..10080) { value -> updateSettings { it.copy(defaultDuration = value) } }
                NumberSetting(tr("Puffer davor", "Buffer before"), state.settings.beforeBuffer, tr("Minuten", "minutes"), 0..1440) { value -> updateSettings { it.copy(beforeBuffer = value) } }
                NumberSetting(tr("Puffer danach", "Buffer after"), state.settings.afterBuffer, tr("Minuten", "minutes"), 0..1440) { value -> updateSettings { it.copy(afterBuffer = value) } }
                TextSetting(tr("Standard-Ort", "Default place"), state.settings.originName) { value -> updateSettings { it.copy(originName = value) } }
                TextSetting(tr("Adresse", "Address"), state.settings.originAddress) { value -> updateSettings { it.copy(originAddress = value) } }
                ChoiceRow(listOf("IGNORE", "RELEVANT", "ALL"), state.settings.attachments) { value -> updateSettings { it.copy(attachments = value) } }
                state.settings.attachmentRules.forEachIndexed { index, rule -> SettingItem(ruleSummary(rule), tr("Erste passende Regel gilt", "First matching rule wins"), onClick = { attachmentEdit = index to rule }) }
                OutlinedButton(onClick = { newAttachment = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Anhangregel", "Attachment rule")) }
                if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Benachrichtigungen erlauben", "Allow notifications")) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vibecoded with ❤️", modifier = Modifier.clickable {
                    val now = android.os.SystemClock.elapsedRealtime(); taps = if (now - lastTap < 1200) taps + 1 else 1; lastTap = now
                    if (taps >= 5) { debugOpen = true; taps = 0 }
                }, style = MaterialTheme.typography.labelLarge)
                Text("Version ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · Debug APK" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/3115a083/KalPlan"))) }) { Text("GitHub") }
                    TextButton(onClick = { noticesOpen = true }) { Text(tr("Lizenzen", "Licenses")) }
                    TextButton(enabled = !busy, onClick = { onRun { feedback = withContext(Dispatchers.IO) { BoundedHttps.json("https://api.github.com/repos/3115a083/KalPlan/releases/latest").getString("tag_name").take(40) } } }) { Text(tr("Update prüfen", "Check update")) }
                }
                if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (debugOpen) item {
            SettingsGroup(Icons.Outlined.BugReport, "Debug", tr("Nur für Testdaten und Diagnose", "Test data and diagnostics only"), initiallyExpanded = true) {
                ToggleRow(tr("Debugmodus", "Debug mode"), state.settings.debug) { enabled -> updateSettings { it.copy(debug = enabled, debugSendToTest = false) } }
                Button(enabled = state.settings.debug && !busy, onClick = { onRun { generateDebugCases(context, repository) } }) { Text(tr("Künstliche Fälle passend zum Kalender erzeugen", "Generate synthetic cases around the calendar")) }
                Text(tr("Erzeugt nur künstliche Absender, Orte und Aufträge. Versand bleibt gesperrt.", "Creates synthetic senders, locations and requests only. Sending stays blocked."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (newAccount || accountEdit != null) AccountDialog(accountEdit, state, repository, busy, onRun) { newAccount = false; accountEdit = null }
    if (newLabel || labelEdit != null) LabelEditorDialog(labelEdit, state.settings.value, onDismiss = { newLabel = false; labelEdit = null }, onSave = { saved ->
        updateSettings { s -> s.copy(labels = s.labels.filterNot { it.name == labelEdit?.name } + saved) }; newLabel = false; labelEdit = null
    }, onDelete = labelEdit?.let { old -> { updateSettings { s -> s.copy(labels = s.labels.filterNot { it.name == old.name }) }; labelEdit = null } })
    if (newProfile || profileEdit != null) ProfileEditorDialog(profileEdit, onDismiss = { newProfile = false; profileEdit = null }, onSave = { saved ->
        onRun { repository.update { it.copy(profiles = it.profiles.filterNot { p -> p.id == saved.id } + saved) } }; newProfile = false; profileEdit = null
    }, onDelete = profileEdit?.let { old -> { onRun { repository.update { it.copy(profiles = it.profiles.filterNot { p -> p.id == old.id }) } }; profileEdit = null } })
    if (valueOpen) ValueEditorDialog(state.settings.value, onDismiss = { valueOpen = false }) { value -> updateSettings { it.copy(value = value) }; valueOpen = false }
    calendarEdit?.let { (calendar, privacy) -> CalendarOptionsDialog(calendar, privacy, state.settings.reservationCalendarId, onDismiss = { calendarEdit = null }) { saved, reservation ->
        onRun { repository.update { it.copy(calendars = it.calendars.filterNot { p -> p.id == saved.id } + saved, settings = it.settings.copy(reservationCalendarId = if (reservation) saved.id else if (it.settings.reservationCalendarId == saved.id) "" else it.settings.reservationCalendarId)) } }; calendarEdit = null
    } }
    if (newAttachment || attachmentEdit != null) AttachmentRuleDialog(attachmentEdit?.second, onDismiss = { newAttachment = false; attachmentEdit = null }) { saved ->
        updateSettings { s -> val rules = s.attachmentRules.toMutableList(); val i = attachmentEdit?.first; if (i == null) rules += saved else rules[i] = saved; s.copy(attachmentRules = rules.take(100)) }; newAttachment = false; attachmentEdit = null
    }
    if (noticesOpen) LicenseDialog { noticesOpen = false }
}

@Composable
private fun SettingsGroup(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable private fun SettingItem(title: String, subtitle: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ThemePicker(selected: String, onSelect: (String) -> Unit) {
    val themes = listOf("MATERIAL_YOU" to Color(0xFF6750A4), "KALPLAN" to Color(0xFF4F52C9), "NEUTRAL_BUSINESS" to Color(0xFF4D5D6C), "TURQUOISE" to Color(0xFF006B62), "HIGH_CONTRAST" to Color(0xFF111111))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        themes.forEach { (name, color) ->
            Surface(modifier = Modifier.width(112.dp).clickable { onSelect(name) }, shape = RoundedCornerShape(16.dp), border = BorderStroke(if (selected == name) 2.dp else 1.dp, if (selected == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.fillMaxWidth().height(34.dp).background(color, RoundedCornerShape(10.dp))); Text(themeName(name), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable private fun themeName(value: String): String = when(value) { "MATERIAL_YOU" -> "Material You"; "NEUTRAL_BUSINESS" -> tr("Neutral", "Neutral"); "TURQUOISE" -> tr("Türkis", "Turquoise"); "HIGH_CONTRAST" -> tr("Kontrast", "Contrast"); else -> "KalPlan" }
private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor("#${hex.removePrefix("#")}")) }.getOrDefault(Color(0xFF6750A4))
private fun labelSummary(label: LabelPolicy): String = (label.keywords.joinToString().ifBlank { label.senderContains }).take(90)
private fun valueSummary(v: ValueSettings): String = "${v.workCentsPerHour / 100.0} €/h · Fahrt ${v.travelCentsPerHour / 100.0} €/h · ${v.centsPerKm / 100.0} €/km · ${v.flatCents / 100.0} €"
@Composable private fun calendarSummary(p: CalendarPrivacy): String = if (!p.included) tr("Aus", "Off") else listOfNotNull(if (p.showTitle) tr("Titel", "title") else null, if (p.showLocation) tr("Ort", "location") else null, if (p.showDescription) tr("Beschreibung", "description") else null, if (p.travelCalendar) tr("Fahrten", "travel") else null).joinToString(" · ")
@Composable private fun ruleSummary(r: AttachmentRule): String = (if (r.show) tr("Zeigen", "Show") else tr("Ausblenden", "Hide")) + " · " + listOf(r.mimePrefix, r.extension, r.nameContains).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { tr("alle passenden Dateien", "all matching files") }

