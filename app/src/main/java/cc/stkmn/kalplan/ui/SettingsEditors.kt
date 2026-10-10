@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cc.stkmn.kalplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
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
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeChoiceButton(tr("Von", "From"), from.ifBlank { "22:00" }, from.isBlank(), Modifier.weight(1f)) { editing = "FROM" }
        TimeChoiceButton(tr("Bis", "Until"), until.ifBlank { "06:00" }, until.isBlank(), Modifier.weight(1f)) { editing = "UNTIL" }
    }
    if (from.isNotBlank() || until.isNotBlank()) TextButton(onClick = { onValue("", "") }) { Text(tr("Ruhezeit entfernen", "Remove quiet hours")) }
    editing?.let { target ->
        val initial = runCatching { LocalTime.parse(if (target == "FROM") from.ifBlank { "22:00" } else until.ifBlank { "06:00" }) }.getOrDefault(if (target == "FROM") LocalTime.of(22, 0) else LocalTime.of(6, 0))
        key(target, initial) {
            ClockPickerDialog(initial, onDismiss = { editing = null }) { selected ->
                val text = "%02d:%02d".format(selected.hour, selected.minute)
                val nextFrom = if (target == "FROM") text else from.ifBlank { "22:00" }
                val nextUntil = if (target == "UNTIL") text else until.ifBlank { "06:00" }
                onValue(nextFrom, nextUntil)
                editing = null
            }
        }
    }
}

@Composable
private fun TimeChoiceButton(label: String, value: String, placeholder: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(58.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(Icons.Outlined.Schedule, null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockPickerDialog(initial: LocalTime, onDismiss: () -> Unit, onSelect: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Uhrzeit auswählen", "Choose time")) }, text = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(picker) }
    }, confirmButton = { TextButton(onClick = { onSelect(LocalTime.of(picker.hour, picker.minute)) }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
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
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(tr("Bearbeiten", "Edit"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun eurosToCents(value: String): Long? = runCatching { BigDecimal(value.replace(',', '.')).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()?.takeIf { it in 0..100_000_000 }
private fun centsText(value: Long?): String = value?.let { BigDecimal(it).divide(BigDecimal(100)).stripTrailingZeros().toPlainString() }.orEmpty()

@Composable
fun ValueEditorDialog(existing: ValueSettings, presets: List<FlatFeePreset>, onDismiss: () -> Unit, onSave: (ValueSettings) -> Unit) {
    var work by rememberSaveable { mutableStateOf(centsText(existing.workCentsPerHour)) }
    var travel by rememberSaveable { mutableStateOf(centsText(existing.travelCentsPerHour)) }
    var km by rememberSaveable { mutableStateOf(centsText(existing.centsPerKm)) }
    var flat by rememberSaveable { mutableStateOf(centsText(existing.flatCents)) }
    var step by rememberSaveable { mutableStateOf(existing.billingStepMinutes.toString()) }
    var roundingMode by rememberSaveable { mutableStateOf(existing.roundingMode.ifBlank { if (existing.roundUp) "UP" else "NEAREST" }) }
    var roundTrip by rememberSaveable { mutableStateOf(existing.roundTrip) }
    var presetId by rememberSaveable { mutableStateOf(existing.flatFeePresetId) }
    val valid = listOf(work, travel, km, flat).all { eurosToCents(it) != null } && step.toIntOrNull()?.let { it in 1..1440 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Globaler Auftragswert", "Global order value")) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField(tr("Auftragszeit pro Stunde", "Work per hour"), work, onValue = { work = it })
            MoneyField(tr("Fahrtzeit pro Stunde", "Travel per hour"), travel, onValue = { travel = it })
            MoneyField(tr("Kilometerpauschale", "Per kilometer"), km, onValue = { km = it })
            MoneyField(tr("Feste Pauschale", "Flat fee"), flat, onValue = { flat = it })
            PresetChoice(presetId, presets) { presetId = it }
            OutlinedTextField(step, { step = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Abrechnungsschritt, Minuten", "Billing increment, minutes")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text(tr("Rundung", "Rounding"), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(listOf("UP", "NEAREST", "DOWN"), roundingMode, label = { when (it) { "DOWN" -> tr("Abrunden", "Round down"); "NEAREST" -> tr("Nächster Schritt", "Nearest"); else -> tr("Aufrunden", "Round up") } }) { roundingMode = it }
            ToggleRow(tr("Hin- und Rückfahrt berechnen", "Calculate round trip"), roundTrip) { roundTrip = it }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(existing.copy(workCentsPerHour = eurosToCents(work)!!, travelCentsPerHour = eurosToCents(travel)!!, centsPerKm = eurosToCents(km)!!, flatCents = eurosToCents(flat)!!, flatFeePresetId = presetId, billingStepMinutes = step.toInt(), roundUp = roundingMode == "UP", roundingMode = roundingMode, roundTrip = roundTrip)) }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
private fun PresetChoice(selected: String, presets: List<FlatFeePreset>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(presets.firstOrNull { it.id == selected }?.name ?: tr("Keine gespeicherte Pauschale", "No saved flat fee"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr("Keine", "None")) }, onClick = { onSelect(""); open = false })
            presets.forEach { preset -> DropdownMenuItem(text = { Text(preset.name) }, onClick = { onSelect(preset.id); open = false }) }
        }
    }
}

@Composable private fun MoneyField(label: String, value: String, onValue: (String) -> Unit, inherited: String? = null) {
    OutlinedTextField(value, { onValue(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12)) }, label = { Text(label) }, placeholder = inherited?.let { { Text(it) } }, suffix = { Text("€") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
fun LabelEditorDialog(existing: LabelPolicy?, global: ValueSettings, presets: List<FlatFeePreset>, onDismiss: () -> Unit, onSave: (LabelPolicy) -> Unit, onDelete: (() -> Unit)?) {
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
    var presetId by rememberSaveable { mutableStateOf(existing?.valueOverride?.flatFeePresetId.orEmpty()) }
    val validMoney = listOf(work, travel, km, flat).all { it.isBlank() || eurosToCents(it) != null }
    val valid = name.isNotBlank() && name.length <= 80 && color.matches(Regex("[0-9a-fA-F]{6}")) && (!durationEnabled || duration.toIntOrNull()?.let { it in 1..10080 } == true) && validMoney
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) tr("Label erstellen", "Create label") else tr("Label bearbeiten", "Edit label")) }, text = {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("Name", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(tr("Farbe", "Color"), style = MaterialTheme.typography.titleSmall)
            LabelColorPicker(color) { color = it }
            Text(tr("Automatisch vergeben, wenn", "Assign automatically when"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newTerm, { newTerm = it.take(100) }, label = { Text(tr("Wort oder Ausdruck", "Word or phrase")) }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(enabled = newTerm.isNotBlank(), onClick = { terms = (terms + newTerm.trim()).distinct(); newTerm = "" }) { Icon(Icons.Outlined.Add, null) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { terms.forEach { term -> InputChip(selected = true, onClick = { terms = terms - term }, label = { Text(term) }, trailingIcon = { Text("×") }) } }
            if (terms.isEmpty() && sender.isBlank()) Text(tr("Ohne Muster wird das Label nur manuell vergeben.", "Without a pattern, the label is assigned manually only."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(listOf("ANY", "ALL"), mode, label = { if (it == "ALL") tr("Alle Begriffe", "All terms") else tr("Ein Begriff", "Any term") }) { mode = it }
            ChoiceRow(listOf("SUBJECT_AND_BODY", "SUBJECT", "BODY"), searchIn, label = { searchTargetName(it) }) { searchIn = it }
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
                MoneyField(tr("Pauschale pro Auftrag", "Flat fee per order"), flat, { flat = it }, centsText(global.flatCents))
                PresetChoice(presetId, presets) { presetId = it }
            }
            if (onDelete != null) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Text(tr("Label löschen", "Delete label")) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        val override = if (!customValue) null else ValueOverride(workCentsPerHour = work.takeIf(String::isNotBlank)?.let(::eurosToCents), travelCentsPerHour = travel.takeIf(String::isNotBlank)?.let(::eurosToCents), centsPerKm = km.takeIf(String::isNotBlank)?.let(::eurosToCents), flatCents = flat.takeIf(String::isNotBlank)?.let(::eurosToCents), flatFeePresetId = presetId.takeIf(String::isNotBlank))
        onSave(LabelPolicy(name = name.trim(), keywords = terms, senderContains = sender.trim(), score = score, durationMinutes = duration.takeIf { durationEnabled }?.toInt(), colorHex = color.uppercase(), keywordMode = mode, searchIn = searchIn, valueOverride = override))
    }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun FlatFeePresetDialog(existing: FlatFeePreset?, onDismiss: () -> Unit, onSave: (FlatFeePreset) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var fixed by rememberSaveable { mutableStateOf(centsText(existing?.flatCents ?: 0)) }
    var bands by remember { mutableStateOf(existing?.distanceBands.orEmpty()) }
    var km by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    val valid = name.isNotBlank() && eurosToCents(fixed) != null && bands.size <= 30
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) tr("Pauschale erstellen", "Create flat fee") else tr("Pauschale bearbeiten", "Edit flat fee")) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("Name", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(tr("Für den ganzen Auftrag", "For the whole order"), fixed, { fixed = it })
                Text(tr("Entfernungsstaffeln (Hin- und Rückweg zusammen)", "Distance bands (round trip total)"), style = MaterialTheme.typography.titleSmall)
                bands.sortedBy { it.upToKm }.forEach { band ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Bis ${band.upToKm.toInt()} km", "Up to ${band.upToKm.toInt()} km") + " · ${band.cents / 100.0} €", Modifier.weight(1f))
                        IconButton(onClick = { bands = bands - band }) { Icon(Icons.Outlined.Delete, tr("Entfernen", "Remove")) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(km, { km = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) }, label = { Text(tr("Bis km", "Up to km")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) }, label = { Text(tr("Betrag", "Amount")) }, suffix = { Text("€") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = km.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true && eurosToCents(amount) != null, onClick = {
                        val limit = km.replace(',', '.').toDouble(); val cents = eurosToCents(amount)!!
                        bands = (bands.filterNot { it.upToKm == limit } + DistanceBand(limit, cents)).sortedBy { it.upToKm }
                        km = ""; amount = ""
                    }) { Icon(Icons.Outlined.Add, tr("Staffel hinzufügen", "Add band")) }
                }
                Text(tr("Beispiele: bis 25 km · 57 €, bis 50 km · 88 €. Die erste passende Staffel wird verwendet.", "Example: up to 25 km · €57, up to 50 km · €88. The first matching band is used."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onDelete != null) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Text(tr("Pauschale löschen", "Delete flat fee")) }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(FlatFeePreset(existing?.id ?: UUID.randomUUID().toString(), name.trim(), eurosToCents(fixed)!!, bands)) }) { Text(tr("Speichern", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

@Composable
fun RoutingSettingsDialog(existingProvider: String, existingLimit: Int, onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit) {
    var provider by rememberSaveable { mutableStateOf(existingProvider) }
    var limit by rememberSaveable { mutableStateOf(existingLimit.toString()) }
    var key by rememberSaveable { mutableStateOf("") }
    val requiresKey = provider !in setOf("MANUAL", "GOOGLE_MAPS")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Fahrtsuche-API", "Routing API")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceRow(listOf("MANUAL", "GOOGLE_MAPS", "HERE", "TOMTOM", "ORS", "GRAPHHOPPER"), provider) { provider = it }
            NumberSetting(tr("Maximale Abfragen pro Tag", "Maximum queries per day"), limit.toIntOrNull() ?: 0, tr("Abfragen", "queries"), 0..10000) { limit = it.toString() }
            if (requiresKey) SecretField(tr("API-Key (leer = vorhandenen behalten)", "API key (blank = keep existing)"), key) { key = it }
            Text(tr("API-Abfragen werden nur nach einer ausdrücklichen Aktion ausgeführt. Der Schlüssel liegt verschlüsselt auf dem Gerät.", "API requests only run after an explicit action. The key is stored encrypted on the device."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(enabled = limit.toIntOrNull()?.let { it in 0..10000 } == true, onClick = { onSave(provider, limit.toInt(), key) }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
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
            if (value.travelCalendar) OutlinedTextField(
                value.travelTitleContains,
                { value = value.copy(travelTitleContains = it.take(160)) },
                label = { Text(tr("Optional: Fahrt-Titel enthält", "Optional: travel title contains")) },
                supportingText = { Text(tr("Nur passende Titel gelten als Fahrt; andere Einträge bleiben normale Termine.", "Only matching titles count as travel; other entries remain normal events.")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ToggleRow(tr("Verborgenen Ort für Fahrtprüfung nutzen", "Use hidden location for travel checks"), value.useHiddenLocationForRouting) { value = value.copy(useHiddenLocationForRouting = it) }
            ToggleRow(tr("Ziel für KalPlan-Reservierungen", "Target for KalPlan reservations"), reservation) { reservation = it }
        }
    }, confirmButton = { TextButton(onClick = { onSave(value.copy(included = true), reservation) }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun AttachmentRuleDialog(existing: AttachmentRule?, onDismiss: () -> Unit, onSave: (AttachmentRule) -> Unit) {
    var show by remember { mutableStateOf(existing?.show ?: true) }; var mime by remember { mutableStateOf("") }; var extension by remember { mutableStateOf("") }; var selectedMimes by remember { mutableStateOf((existing?.mimePrefixes.orEmpty() + existing?.mimePrefix.orEmpty()).filter { it.isNotBlank() }.distinct()) }; var selectedExtensions by remember { mutableStateOf((existing?.extensions.orEmpty() + existing?.extension.orEmpty()).filter { it.isNotBlank() }.distinct()) }; var name by remember { mutableStateOf(existing?.nameContains.orEmpty()) }; var min by remember { mutableStateOf(existing?.minBytes?.div(1024)?.toString().orEmpty()) }; var max by remember { mutableStateOf(existing?.maxBytes?.div(1024)?.toString().orEmpty()) }; var inline by remember { mutableStateOf(existing?.inline?.toString() ?: "ANY") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Anhangregel", "Attachment rule")) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(tr("Passende Dateien anzeigen", "Show matching files"), show) { show = it }
        Text(tr("Formate auswählen", "Choose formats"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("application/pdf" to "PDF", "image/jpeg" to "JPEG", "image/png" to "PNG", "application/vnd.openxmlformats" to "Office", "text/" to tr("Text", "Text")).forEach { (value, label) ->
                FilterChip(selected = value in selectedMimes, onClick = { selectedMimes = if (value in selectedMimes) selectedMimes - value else selectedMimes + value }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(mime, { mime = it.take(100) }, label = { Text(tr("Weiterer MIME-Typ", "Another MIME type")) }, singleLine = true, modifier = Modifier.weight(1f)); IconButton(enabled = mime.isNotBlank(), onClick = { selectedMimes = (selectedMimes + mime.trim()).distinct(); mime = "" }) { Icon(Icons.Outlined.Add, null) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { selectedMimes.forEach { value -> InputChip(selected = true, onClick = { selectedMimes = selectedMimes - value }, label = { Text(value) }, trailingIcon = { Text("×") }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(extension, { extension = it.removePrefix(".").take(20) }, label = { Text(tr("Weitere Dateiendung", "Another extension")) }, singleLine = true, modifier = Modifier.weight(1f)); IconButton(enabled = extension.isNotBlank(), onClick = { selectedExtensions = (selectedExtensions + extension.trim()).distinct(); extension = "" }) { Icon(Icons.Outlined.Add, null) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { selectedExtensions.forEach { value -> InputChip(selected = true, onClick = { selectedExtensions = selectedExtensions - value }, label = { Text(".$value") }, trailingIcon = { Text("×") }) } }
        OutlinedTextField(name, { name = it.take(100) }, label = { Text(tr("Dateiname enthält", "Filename contains")) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(min, { min = it.filter(Char::isDigit).take(9) }, label = { Text(tr("Min. KB", "Min KB")) }, modifier = Modifier.weight(1f)); OutlinedTextField(max, { max = it.filter(Char::isDigit).take(9) }, label = { Text(tr("Max. KB", "Max KB")) }, modifier = Modifier.weight(1f)) }
        ChoiceRow(listOf("ANY", "true", "false"), inline, label = { when(it) { "true" -> tr("Eingebettet", "Inline"); "false" -> tr("Angehängt", "Attached"); else -> tr("Beides", "Either") } }) { inline = it }
    } }, confirmButton = { TextButton(onClick = { onSave(AttachmentRule(show = show, nameContains = name.trim(), minBytes = min.toIntOrNull()?.times(1024), maxBytes = max.toIntOrNull()?.times(1024), inline = inline.toBooleanStrictOrNull(), mimePrefixes = selectedMimes.take(20), extensions = selectedExtensions.take(20))) }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
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
                    Column(Modifier.weight(1f)) { Text(rule.sampleLabel ?: rule.key); Text(semanticName(rule.semantic.name) + " · " + sourceName(rule.source.name) + if (rule.required) " · " + tr("Pflicht", "required") else "", style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { rules = rules.filterNot { it.id == rule.id } }) { Icon(Icons.Outlined.Delete, null) }
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Feld hinzufügen", "Add field"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(tr("Trage die sichtbare Bezeichnung vor dem Wert ein, zum Beispiel „Termin“ oder „Ort“.", "Enter the visible label before the value, for example “Date” or “Location”."), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(prefix, { prefix = it.take(100) }, label = { Text(tr("Bezeichnung in der Mail", "Label in the email")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ChoiceRow(listOf("DATE", "TIME", "END_TIME", "DURATION", "LOCATION", "ONLINE_OR_LOCATION", "TITLE"), semantic, label = { semanticName(it) }) { semantic = it }
                    ChoiceRow(listOf("BODY", "SUBJECT"), source, label = { sourceName(it) }) { source = it }
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
            ChoiceRow(listOf("DE_DE", "EN_US"), locale, label = { if (it == "EN_US") "English (US)" else "Deutsch (DE)" }) { locale = it }
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
private fun LabelColorPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("6750A4", "4F52C9", "2457C5", "006B62", "168A55", "3B7A57", "7A8B2E", "B26A00", "E56B1F", "C2415B", "A33B20", "8055A5", "35618D", "5D6B78")
    var customOpen by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { hex ->
            val value = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#$hex"))
            Surface(modifier = Modifier.size(40.dp).clickable { onSelect(hex) }, shape = RoundedCornerShape(12.dp), color = value,
                border = BorderStroke(if (selected.equals(hex, true)) 3.dp else 1.dp, if (selected.equals(hex, true)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)) {
                if (selected.equals(hex, true)) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, tr("Ausgewählt", "Selected"), tint = androidx.compose.ui.graphics.Color.White) }
            }
        }
        Surface(
            modifier = Modifier.size(40.dp).clickable { customOpen = true },
            shape = RoundedCornerShape(12.dp),
            color = if (selected.uppercase() !in colors) parseEditorColor(selected) else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(if (selected.uppercase() !in colors) 3.dp else 1.dp, MaterialTheme.colorScheme.outline)
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, tr("Eigene Farbe", "Custom color")) } }
    }
    if (customOpen) CustomLabelColorDialog(selected, onDismiss = { customOpen = false }) {
        onSelect(it); customOpen = false
    }
}

private fun parseEditorColor(hex: String) = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#${hex.removePrefix("#")}")) }.getOrDefault(androidx.compose.ui.graphics.Color.Gray)

@Composable
private fun CustomLabelColorDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val initial = remember(selected) {
        FloatArray(3).also { hsv ->
            val color = runCatching { android.graphics.Color.parseColor("#${selected.removePrefix("#")}") }.getOrDefault(android.graphics.Color.rgb(79, 82, 201))
            android.graphics.Color.colorToHSV(color, hsv)
        }
    }
    var hue by rememberSaveable { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initial[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initial[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val hex = "%06X".format(argb and 0xFFFFFF)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eigene Label-Farbe", "Custom label color")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.fillMaxWidth().height(72.dp), color = androidx.compose.ui.graphics.Color(argb), shape = RoundedCornerShape(18.dp)) { }
                Text(tr("Farbton", "Hue")); Slider(hue, { hue = it }, valueRange = 0f..360f)
                Text(tr("Sättigung", "Saturation")); Slider(saturation, { saturation = it }, valueRange = 0f..1f)
                Text(tr("Helligkeit", "Brightness")); Slider(brightness, { brightness = it }, valueRange = 0.18f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(hex) }) { Text(tr("Übernehmen", "Apply")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

@Composable internal fun semanticName(value: String): String = when(value) {
    "DATE" -> tr("Datum", "Date"); "TIME" -> tr("Beginn", "Start time"); "END_TIME" -> tr("Ende", "End time")
    "DURATION" -> tr("Dauer", "Duration"); "LOCATION" -> tr("Ort", "Location"); "ONLINE_OR_LOCATION" -> tr("Online oder Ort", "Online or location")
    else -> tr("Titel", "Title")
}
@Composable internal fun sourceName(value: String): String = if (value == "SUBJECT") tr("Betreff", "Subject") else tr("Mailtext", "Email body")
@Composable private fun searchTargetName(value: String): String = when(value) { "SUBJECT" -> tr("Betreff", "Subject"); "BODY" -> tr("Mailtext", "Email body"); else -> tr("Betreff und Mailtext", "Subject and email body") }

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

