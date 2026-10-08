@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cc.stkmn.kalplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.CalendarRef
import cc.stkmn.kalplan.extraction.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import java.util.UUID

@Composable
fun NumberSetting(label: String, value: Int, suffix: String, range: IntRange, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { next ->
        text = next.filter(Char::isDigit).take(6)
        text.toIntOrNull()?.takeIf { it in range }?.let(onValue)
    }, label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

@Composable
fun TextSetting(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, { onValue(it.take(1000)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun TimeRangeRow(from: String, until: String, onValue: (String, String) -> Unit) {
    var start by remember(from) { mutableStateOf(from) }
    var end by remember(until) { mutableStateOf(until) }
    fun commit(a: String, b: String) { if (a.isBlank() && b.isBlank() || runCatching { LocalTime.parse(a); LocalTime.parse(b) }.isSuccess) onValue(a, b) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it.take(5); commit(start, end) }, label = { Text(tr("Von", "From")) }, placeholder = { Text("22:00") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(end, { end = it.take(5); commit(start, end) }, label = { Text(tr("Bis", "Until")) }, placeholder = { Text("06:00") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    if (start.isNotBlank() || end.isNotBlank()) TextButton(onClick = { start = ""; end = ""; onValue("", "") }) { Text(tr("Ruhezeit entfernen", "Remove quiet hours")) }
}

@Composable
fun TemplateEditor(label: String, value: String, onValue: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingEditorRow(label, value.lineSequence().firstOrNull().orEmpty()) { open = true }
    if (open) {
        var draft by remember(value) { mutableStateOf(value) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it.take(100_000) }, minLines = 7, modifier = Modifier.fillMaxWidth())
                Text("{date} · {time} · {subject} · {sender}", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { onValue(draft); open = false }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Abbrechen", "Cancel")) } })
    }
}

@Composable private fun SettingEditorRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun eurosToCents(value: String): Long? = runCatching { BigDecimal(value.replace(',', '.')).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()?.takeIf { it in 0..100_000_000 }
private fun centsText(value: Long?): String = value?.let { BigDecimal(it).divide(BigDecimal(100)).stripTrailingZeros().toPlainString() }.orEmpty()

@Composable
fun ValueEditorDialog(existing: ValueSettings, onDismiss: () -> Unit, onSave: (ValueSettings) -> Unit) {
    var work by rememberSaveable { mutableStateOf(centsText(existing.workCentsPerHour)) }
    var travel by rememberSaveable { mutableStateOf(centsText(existing.travelCentsPerHour)) }
    var km by rememberSaveable { mutableStateOf(centsText(existing.centsPerKm)) }
    var flat by rememberSaveable { mutableStateOf(centsText(existing.flatCents)) }
    var step by rememberSaveable { mutableStateOf(existing.billingStepMinutes.toString()) }
    var roundUp by rememberSaveable { mutableStateOf(existing.roundUp) }
    var roundTrip by rememberSaveable { mutableStateOf(existing.roundTrip) }
    val valid = listOf(work, travel, km, flat).all { eurosToCents(it) != null } && step.toIntOrNull()?.let { it in 1..1440 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Globaler Auftragswert", "Global order value")) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField(tr("Auftragszeit pro Stunde", "Work per hour"), work, onValue = { work = it })
            MoneyField(tr("Fahrtzeit pro Stunde", "Travel per hour"), travel, onValue = { travel = it })
            MoneyField(tr("Kilometerpauschale", "Per kilometer"), km, onValue = { km = it })
            MoneyField(tr("Feste Pauschale", "Flat fee"), flat, onValue = { flat = it })
            OutlinedTextField(step, { step = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Abrechnungsschritt, Minuten", "Billing increment, minutes")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            ToggleRow(tr("Aufrunden", "Round up"), roundUp) { roundUp = it }
            ToggleRow(tr("Hin- und Rückfahrt berechnen", "Calculate round trip"), roundTrip) { roundTrip = it }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(existing.copy(workCentsPerHour = eurosToCents(work)!!, travelCentsPerHour = eurosToCents(travel)!!, centsPerKm = eurosToCents(km)!!, flatCents = eurosToCents(flat)!!, billingStepMinutes = step.toInt(), roundUp = roundUp, roundTrip = roundTrip)) }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable private fun MoneyField(label: String, value: String, onValue: (String) -> Unit, inherited: String? = null) {
    OutlinedTextField(value, { onValue(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12)) }, label = { Text(label) }, placeholder = inherited?.let { { Text(it) } }, suffix = { Text("€") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
fun LabelEditorDialog(existing: LabelPolicy?, global: ValueSettings, onDismiss: () -> Unit, onSave: (LabelPolicy) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(existing?.colorHex ?: "6750A4") }
    var terms by rememberSaveable { mutableStateOf(existing?.keywords ?: emptyList()) }
    var newTerm by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf(existing?.senderContains.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf(existing?.keywordMode ?: "ANY") }
    var searchIn by rememberSaveable { mutableStateOf(existing?.searchIn ?: "SUBJECT_AND_BODY") }
    var score by rememberSaveable { mutableIntStateOf(existing?.score ?: 0) }
    var durationEnabled by rememberSaveable { mutableStateOf(existing?.durationMinutes != null) }
    var duration by rememberSaveable { mutableStateOf(existing?.durationMinutes?.toString() ?: "60") }
    var customValue by rememberSaveable { mutableStateOf(existing?.valueOverride != null) }
    var work by rememberSaveable { mutableStateOf(centsText(existing?.valueOverride?.workCentsPerHour)) }
    var travel by rememberSaveable { mutableStateOf(centsText(existing?.valueOverride?.travelCentsPerHour)) }
    var km by rememberSaveable { mutableStateOf(centsText(existing?.valueOverride?.centsPerKm)) }
    var flat by rememberSaveable { mutableStateOf(centsText(existing?.valueOverride?.flatCents)) }
    val validMoney = listOf(work, travel, km, flat).all { it.isBlank() || eurosToCents(it) != null }
    val valid = name.isNotBlank() && name.length <= 80 && color.matches(Regex("[0-9a-fA-F]{6}")) && (terms.isNotEmpty() || sender.isNotBlank()) && (!durationEnabled || duration.toIntOrNull()?.let { it in 1..10080 } == true) && validMoney
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) tr("Label erstellen", "Create label") else tr("Label bearbeiten", "Edit label")) }, text = {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("Name", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(color, { color = it.removePrefix("#").filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(6) }, label = { Text(tr("Farbe, z. B. 6750A4", "Color, e.g. 6750A4")) }, leadingIcon = { Box(Modifier.size(18.dp).background(runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#$color")) }.getOrDefault(androidx.compose.ui.graphics.Color.Gray), RoundedCornerShape(5.dp))) }, singleLine = true)
            Text(tr("Automatisch vergeben, wenn", "Assign automatically when"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newTerm, { newTerm = it.take(100) }, label = { Text(tr("Wort oder Ausdruck", "Word or phrase")) }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(enabled = newTerm.isNotBlank(), onClick = { terms = (terms + newTerm.trim()).distinct(); newTerm = "" }) { Icon(Icons.Outlined.Add, null) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { terms.forEach { term -> InputChip(selected = true, onClick = { terms = terms - term }, label = { Text(term) }, trailingIcon = { Text("×") }) } }
            ChoiceRow(listOf("ANY", "ALL"), mode) { mode = it }
            ChoiceRow(listOf("SUBJECT_AND_BODY", "SUBJECT", "BODY"), searchIn) { searchIn = it }
            OutlinedTextField(sender, { sender = it.take(320) }, label = { Text(tr("Optional: Absender enthält", "Optional: sender contains")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(tr("Sortiergewicht", "Sorting weight"), style = MaterialTheme.typography.titleSmall)
            Slider(score.toFloat(), { score = it.toInt() }, valueRange = -40f..40f, steps = 7)
            Text(when { score >= 25 -> tr("deutlich höher", "much higher"); score >= 10 -> tr("höher", "higher"); score > -10 -> tr("neutral", "neutral"); score > -25 -> tr("niedriger", "lower"); else -> tr("deutlich niedriger", "much lower") })
            ToggleRow(tr("Eigene Standarddauer", "Custom default duration"), durationEnabled) { durationEnabled = it }
            if (durationEnabled) OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(5) }, label = { Text(tr("Minuten", "Minutes")) }, singleLine = true)
            ToggleRow(tr("Auftragswert für dieses Label anpassen", "Adjust order value for this label"), customValue) { customValue = it }
            if (customValue) {
                Text(tr("Leere Felder übernehmen den globalen Standard. Nur Abweichungen müssen eingetragen werden.", "Empty fields inherit the global default. Enter differences only."), style = MaterialTheme.typography.bodySmall)
                MoneyField(tr("Auftragszeit pro Stunde", "Work per hour"), work, { work = it }, centsText(global.workCentsPerHour))
                MoneyField(tr("Fahrtzeit pro Stunde", "Travel per hour"), travel, { travel = it }, centsText(global.travelCentsPerHour))
                MoneyField(tr("Kilometerpauschale", "Per kilometer"), km, { km = it }, centsText(global.centsPerKm))
                MoneyField(tr("Fahr- oder Auftragspauschale", "Travel or order flat fee"), flat, { flat = it }, centsText(global.flatCents))
            }
            if (onDelete != null) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Text(tr("Label löschen", "Delete label")) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        val override = if (!customValue) null else ValueOverride(work.takeIf(String::isNotBlank)?.let(::eurosToCents), travel.takeIf(String::isNotBlank)?.let(::eurosToCents), km.takeIf(String::isNotBlank)?.let(::eurosToCents), flat.takeIf(String::isNotBlank)?.let(::eurosToCents))
        onSave(LabelPolicy(name = name.trim(), keywords = terms, senderContains = sender.trim(), score = score, durationMinutes = duration.takeIf { durationEnabled }?.toInt(), colorHex = color.uppercase(), keywordMode = mode, searchIn = searchIn, valueOverride = override))
    }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun CalendarOptionsDialog(calendar: CalendarRef, existing: CalendarPrivacy, reservationId: String, onDismiss: () -> Unit, onSave: (CalendarPrivacy, Boolean) -> Unit) {
    var value by remember { mutableStateOf(existing) }
    var reservation by remember { mutableStateOf(reservationId == calendar.id) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(calendar.displayName) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Lege einmal fest, wie dieser Kalender verwendet wird. Später genügt ein Tipp auf den Kalender.", "Choose once how this calendar is used. Tap the calendar later to change it."))
            ToggleRow(tr("Titel anzeigen", "Show title"), value.showTitle) { value = value.copy(showTitle = it) }
            ToggleRow(tr("Ort anzeigen", "Show location"), value.showLocation) { value = value.copy(showLocation = it) }
            ToggleRow(tr("Beschreibung anzeigen", "Show description"), value.showDescription) { value = value.copy(showDescription = it) }
            ToggleRow(tr("Als Fahrtenkalender verwenden", "Use as travel calendar"), value.travelCalendar) { value = value.copy(travelCalendar = it) }
            ToggleRow(tr("Verborgenen Ort für Fahrtprüfung nutzen", "Use hidden location for travel checks"), value.useHiddenLocationForRouting) { value = value.copy(useHiddenLocationForRouting = it) }
            ToggleRow(tr("Ziel für KalPlan-Reservierungen", "Target for KalPlan reservations"), reservation) { reservation = it }
        }
    }, confirmButton = { TextButton(onClick = { onSave(value.copy(included = true), reservation) }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun AttachmentRuleDialog(existing: AttachmentRule?, onDismiss: () -> Unit, onSave: (AttachmentRule) -> Unit) {
    var show by remember { mutableStateOf(existing?.show ?: true) }; var mime by remember { mutableStateOf(existing?.mimePrefix.orEmpty()) }; var extension by remember { mutableStateOf(existing?.extension.orEmpty()) }; var name by remember { mutableStateOf(existing?.nameContains.orEmpty()) }; var min by remember { mutableStateOf(existing?.minBytes?.div(1024)?.toString().orEmpty()) }; var max by remember { mutableStateOf(existing?.maxBytes?.div(1024)?.toString().orEmpty()) }; var inline by remember { mutableStateOf(existing?.inline?.toString() ?: "ANY") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Anhangregel", "Attachment rule")) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(tr("Passende Dateien anzeigen", "Show matching files"), show) { show = it }
        OutlinedTextField(mime, { mime = it.take(100) }, label = { Text("MIME " + tr("beginnt mit", "starts with")) }, singleLine = true)
        OutlinedTextField(extension, { extension = it.removePrefix(".").take(20) }, label = { Text(tr("Dateiendung", "File extension")) }, singleLine = true)
        OutlinedTextField(name, { name = it.take(100) }, label = { Text(tr("Dateiname enthält", "Filename contains")) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(min, { min = it.filter(Char::isDigit).take(9) }, label = { Text(tr("Min. KB", "Min KB")) }, modifier = Modifier.weight(1f)); OutlinedTextField(max, { max = it.filter(Char::isDigit).take(9) }, label = { Text(tr("Max. KB", "Max KB")) }, modifier = Modifier.weight(1f)) }
        ChoiceRow(listOf("ANY", "true", "false"), inline) { inline = it }
    } }, confirmButton = { TextButton(onClick = { onSave(AttachmentRule(show, mime.trim(), extension.trim(), name.trim(), min.toIntOrNull()?.times(1024), max.toIntOrNull()?.times(1024), inline.toBooleanStrictOrNull())) }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun ProfileEditorDialog(existing: ExtractionProfile?, onDismiss: () -> Unit, onSave: (ExtractionProfile) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var subjectContains by rememberSaveable { mutableStateOf("") }
    var senderContains by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(existing?.defaultDurationMinutes?.toString().orEmpty()) }
    var locale by rememberSaveable { mutableStateOf(existing?.locale?.name ?: "DE_DE") }
    var rules by remember { mutableStateOf(existing?.extractors ?: emptyList()) }
    var prefix by rememberSaveable { mutableStateOf("") }
    var semantic by rememberSaveable { mutableStateOf("DATE") }
    var source by rememberSaveable { mutableStateOf("BODY") }
    var required by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank() && rules.isNotEmpty() && (duration.isBlank() || duration.toIntOrNull()?.let { it in 1..10080 } == true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) tr("Profil erstellen", "Create profile") else tr("Profil bearbeiten", "Edit profile")) }, text = {
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text(tr("Profilname", "Profile name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ToggleRow(tr("Aktiv", "Enabled"), enabled) { enabled = it }
            Text(tr("Wann passt das Profil?", "When does this profile apply?"), style = MaterialTheme.typography.titleSmall)
            Text(tr("Die Zuordnung zu einem Mailordner bleibt die zuverlässigste Auswahl. Diese optionalen Merkmale helfen bei mehreren Profilen.", "Assigning a profile to a mail folder is the most reliable selection. These optional traits help with multiple profiles."), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(subjectContains, { subjectContains = it.take(200) }, label = { Text(tr("Betreff enthält", "Subject contains")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(senderContains, { senderContains = it.take(320) }, label = { Text(tr("Absender enthält", "Sender contains")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (existing?.matchers?.isNotEmpty() == true && subjectContains.isBlank() && senderContains.isBlank()) Text(tr("Bestehende Profilmerkmale bleiben unverändert.", "Existing profile traits remain unchanged."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Erkannte Felder", "Extracted fields"), style = MaterialTheme.typography.titleSmall)
            rules.forEach { rule ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(rule.sampleLabel ?: rule.key); Text(rule.semantic.name + " · " + rule.source.name + if (rule.required) " · " + tr("Pflicht", "required") else "", style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { rules = rules.filterNot { it.id == rule.id } }) { Icon(Icons.Outlined.Delete, null) }
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Feld ohne Regex hinzufügen", "Add field without regex"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(tr("Trage die sichtbare Bezeichnung vor dem Wert ein, zum Beispiel „Termin“ oder „Ort“.", "Enter the visible label before the value, for example “Date” or “Location”."), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(prefix, { prefix = it.take(100) }, label = { Text(tr("Bezeichnung in der Mail", "Label in the email")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ChoiceRow(listOf("DATE", "TIME", "END_TIME", "DURATION", "LOCATION", "ONLINE_OR_LOCATION", "TITLE"), semantic) { semantic = it }
                    ChoiceRow(listOf("BODY", "SUBJECT"), source) { source = it }
                    ToggleRow(tr("Pflichtfeld", "Required field"), required) { required = it }
                    OutlinedButton(enabled = prefix.isNotBlank(), onClick = {
                        val key = GuidedRuleFactory.sanitizeKey(prefix).ifBlank { "field_${rules.size + 1}" }
                        val regex = if (source == "SUBJECT") "(?s)^\\s*(.+?)\\s*$" else "(?m)^\\s*" + Regex.escape(prefix.trim()) + "\\s*[:=–-]\\s*(.+?)\\s*$"
                        rules = rules + ExtractorRule("visual_${UUID.randomUUID()}", key, SemanticField.valueOf(semantic), regex, required = required, source = InputSource.valueOf(source), sampleLabel = prefix.trim())
                        prefix = ""; required = false
                    }) { Icon(Icons.Outlined.Add, null); Text(tr("Feld hinzufügen", "Add field")) }
                }
            }
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(5) }, label = { Text(tr("Optionale Standarddauer, Minuten", "Optional default duration, minutes")) }, singleLine = true)
            ChoiceRow(listOf("DE_DE", "EN_US"), locale) { locale = it }
            if (onDelete != null) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Text(tr("Profil löschen", "Delete profile")) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        val matchers = if (subjectContains.isBlank() && senderContains.isBlank()) existing?.matchers.orEmpty() else buildList {
            if (subjectContains.isNotBlank()) add(MatcherRule("visual_subject", Regex.escape(subjectContains.trim()), InputSource.SUBJECT))
            if (senderContains.isNotBlank()) add(MatcherRule("visual_sender", Regex.escape(senderContains.trim()), InputSource.SENDER))
        }
        val saved = ExtractionProfile(id = existing?.id ?: UUID.randomUUID().toString(), name = name.trim(), enabled = enabled, matchers = matchers, extractors = rules, defaultDurationMinutes = duration.toIntOrNull(), locale = TemporalLocale.valueOf(locale), acceptTemplate = existing?.acceptTemplate, declineTemplate = existing?.declineTemplate)
        if (ProfileValidator().validate(saved).isEmpty()) onSave(saved)
    }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val files = remember { context.assets.list("licenses").orEmpty().sorted() }
    var selected by remember { mutableStateOf<String?>(null) }
    val text = remember(selected) { selected?.let { context.assets.open("licenses/$it").bufferedReader().use { reader -> reader.readText() } }.orEmpty() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Open-Source-Lizenzen", "Open-source licenses")) }, text = {
        if (selected == null) Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) { files.forEach { file -> TextButton(onClick = { selected = file }) { Text(file) } } }
        else androidx.compose.foundation.text.selection.SelectionContainer { Text(text, Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) }
    }, confirmButton = { TextButton(onClick = { if (selected != null) selected = null else onDismiss() }) { Text(if (selected != null) tr("Zurück", "Back") else tr("Schließen", "Close")) } })
}

