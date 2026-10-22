@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cc.stkmn.kalplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
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
    var open by rememberSaveable { mutableStateOf(false) }
    SettingEditorRow(label, "$value $suffix") { open = true }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(value.toString()) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            StableTextField(draft, { draft = it.filter(Char::isDigit).take(6) }, label = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }, confirmButton = { TextButton(enabled = draft.toIntOrNull()?.let { it in range } == true,
            onClick = { onValue(draft.toInt()); open = false }) { Text(tr("Übernehmen", "Apply")) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Abbrechen", "Cancel")) } })
    }
}

@Composable
fun TextSetting(label: String, value: String, onValue: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingEditorRow(label, value) { open = true }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(value) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            StableTextField(draft, { draft = it.take(1000) }, label = { Text(label) }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { onValue(draft); open = false }) { Text(tr("Übernehmen", "Apply")) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Abbrechen", "Cancel")) } })
    }
}

@Composable
fun SyncScheduleSettings(settings: Settings, enabled: Boolean = true, onChange: (Settings) -> Unit) {
    Text(tr("Aktive Sync-Wochentage", "Active sync weekdays"), style = MaterialTheme.typography.labelLarge)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            FilterChip(selected = day in settings.activeSyncWeekdays, enabled = enabled,
                onClick = { onChange(settings.copy(activeSyncWeekdays = if (day in settings.activeSyncWeekdays) settings.activeSyncWeekdays - day else settings.activeSyncWeekdays + day, pauseWeekends = false, pausedWeekdays = emptySet())) },
                label = { Text(java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, locale)) })
        }
    }
    if (settings.activeSyncWeekdays.isEmpty()) Text(tr("Keine Tage aktiv. Manuelles Aktualisieren bleibt möglich.", "No days selected. Manual refresh remains available."), style = MaterialTheme.typography.bodySmall)
    val toggleQuietHours: (Boolean) -> Unit = { checked ->
        onChange(settings.copy(quietHoursEnabled = checked,
            quietFrom = if (checked && settings.quietFrom.isBlank()) "22:00" else settings.quietFrom,
            quietUntil = if (checked && settings.quietUntil.isBlank()) "06:00" else settings.quietUntil))
    }
    Row(Modifier.fillMaxWidth().toggleable(value = settings.quietHoursEnabled, enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Switch, onValueChange = toggleQuietHours), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Tägliche Ruhezeit aktivieren", "Enable daily quiet hours"), Modifier.weight(1f))
        Switch(checked = settings.quietHoursEnabled, enabled = enabled, onCheckedChange = null)
    }
    if (settings.quietHoursEnabled) {
        TimeRangeRow(settings.quietFrom, settings.quietUntil, enabled = enabled) { from, until -> onChange(settings.copy(quietFrom = from, quietUntil = until)) }
        Text(tr("Über Mitternacht ist möglich, z. B. 22:00 bis 06:00.", "Overnight ranges are supported, e.g. 22:00 to 06:00."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TimeRangeRow(from: String, until: String, enabled: Boolean = true, onValue: (String, String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeChoiceButton(tr("Von", "From"), from.ifBlank { "22:00" }, from.isBlank(), Modifier.weight(1f), enabled) { editing = "FROM" }
        TimeChoiceButton(tr("Bis", "Until"), until.ifBlank { "06:00" }, until.isBlank(), Modifier.weight(1f), enabled) { editing = "UNTIL" }
    }
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
private fun TimeChoiceButton(label: String, value: String, placeholder: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(58.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
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
                StableTextField(draft, { draft = it.take(100_000) }, minLines = 7, modifier = Modifier.fillMaxWidth())
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
    var presetIds by rememberSaveable { mutableStateOf((existing.flatFeePresetIds + listOf(existing.flatFeePresetId)).filter(String::isNotBlank).distinct()) }
    val valid = listOf(work, travel, km, flat).all { eurosToCents(it) != null } && step.toIntOrNull()?.let { it in 1..1440 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Globaler Auftragswert", "Global order value")) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField(tr("Auftragszeit pro Stunde", "Work per hour"), work, onValue = { work = it })
            MoneyField(tr("Fahrtzeit pro Stunde", "Travel per hour"), travel, onValue = { travel = it })
            MoneyField(tr("Kilometerpauschale", "Per kilometer"), km, onValue = { km = it })
            MoneyField(tr("Feste Pauschale", "Flat fee"), flat, onValue = { flat = it })
            PresetMultiChoice(presetIds, presets) { presetIds = it }
            StableTextField(step, { step = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Abrechnungsschritt, Minuten", "Billing increment, minutes")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text(tr("Rundung", "Rounding"), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(listOf("UP", "NEAREST", "DOWN"), roundingMode, label = { when (it) { "DOWN" -> tr("Abrunden", "Round down"); "NEAREST" -> tr("Nächster Schritt", "Nearest"); else -> tr("Aufrunden", "Round up") } }) { roundingMode = it }
            ToggleRow(tr("Hin- und Rückfahrt berechnen", "Calculate round trip"), roundTrip) { roundTrip = it }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(existing.copy(workCentsPerHour = eurosToCents(work)!!, travelCentsPerHour = eurosToCents(travel)!!, centsPerKm = eurosToCents(km)!!, flatCents = eurosToCents(flat)!!, flatFeePresetId = "", flatFeePresetIds = presetIds, billingStepMinutes = step.toInt(), roundUp = roundingMode == "UP", roundingMode = roundingMode, roundTrip = roundTrip)) }) { Text(tr("Übernehmen", "Apply")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
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
    StableTextField(value, { onValue(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12)) }, label = { Text(label) }, placeholder = inherited?.let { { Text(it) } }, suffix = { Text("€") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
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
    var presetIds by rememberSaveable { mutableStateOf((existing?.valueOverride?.flatFeePresetIds.orEmpty() + listOf(existing?.valueOverride?.flatFeePresetId.orEmpty())).filter(String::isNotBlank).distinct()) }
    var groups by remember { mutableStateOf(editableLabelGroups(existing)) }
    val validMoney = listOf(work, travel, km, flat).all { it.isBlank() || eurosToCents(it) != null }
    val valid = name.isNotBlank() && name.length <= 80 && color.matches(Regex("[0-9a-fA-F]{6}")) && (!durationEnabled || duration.toIntOrNull()?.let { it in 1..10080 } == true) && (!customValue || validMoney) && groups.all { it.conditions.isNotEmpty() && it.conditions.all { c -> c.text.isNotBlank() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) tr("Label erstellen", "Create label") else tr("Label bearbeiten", "Edit label")) }, text = {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StableTextField(name, { name = it.take(80) }, label = { Text(tr("Name", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(tr("Farbe", "Color"), style = MaterialTheme.typography.titleSmall)
            LabelColorPicker(color) { color = it }
            Text(tr("Automatisch vergeben, wenn", "Assign automatically when"), style = MaterialTheme.typography.titleSmall)
            LabelGroupsEditor(groups) { groups = it }
            Text(tr("Sortiergewicht", "Sorting weight"), style = MaterialTheme.typography.titleSmall)
            Slider(score.toFloat(), { score = it.toInt() }, valueRange = -40f..40f, steps = 7)
            Text(when { score >= 25 -> tr("deutlich höher", "much higher"); score >= 10 -> tr("höher", "higher"); score > -10 -> tr("neutral", "neutral"); score > -25 -> tr("niedriger", "lower"); else -> tr("deutlich niedriger", "much lower") })
            ToggleRow(tr("Eigene Standarddauer", "Custom default duration"), durationEnabled) { durationEnabled = it }
            if (durationEnabled) StableTextField(duration, { duration = it.filter(Char::isDigit).take(5) }, label = { Text(tr("Minuten", "Minutes")) }, singleLine = true)
            ToggleRow(tr("Auftragswert für dieses Label anpassen", "Adjust order value for this label"), customValue) { customValue = it }
            if (customValue) {
                Text(tr("Leere Preisfelder gelten als 0. Der globale Standard gilt nur, wenn diese Anpassung ausgeschaltet ist.", "Empty price fields mean 0. The global default applies only when this adjustment is switched off."), style = MaterialTheme.typography.bodySmall)
                MoneyField(tr("Auftragszeit pro Stunde", "Work per hour"), work, { work = it }, "0")
                MoneyField(tr("Fahrtzeit pro Stunde", "Travel per hour"), travel, { travel = it }, "0")
                MoneyField(tr("Kilometerpauschale", "Per kilometer"), km, { km = it }, "0")
                MoneyField(tr("Pauschale pro Auftrag", "Flat fee per order"), flat, { flat = it }, "0")
                PresetMultiChoice(presetIds, presets) { presetIds = it }
            }
            if (onDelete != null) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Text(tr("Label löschen", "Delete label")) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        val override = if (!customValue) null else ValueOverride(workCentsPerHour = if (work.isBlank()) 0L else eurosToCents(work), travelCentsPerHour = if (travel.isBlank()) 0L else eurosToCents(travel), centsPerKm = if (km.isBlank()) 0L else eurosToCents(km), flatCents = if (flat.isBlank()) 0L else eurosToCents(flat), flatFeePresetId = "", flatFeePresetIds = presetIds)
        onSave((existing ?: LabelPolicy(name = name.trim())).copy(name = name.trim(), keywords = emptyList(), senderContains = "", conditionGroups = groups, score = score, durationMinutes = duration.takeIf { durationEnabled }?.toInt(), colorHex = color.uppercase(), keywordMode = mode, searchIn = searchIn, valueOverride = override))
    }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
fun FlatFeePresetDialog(existing: FlatFeePreset?, onDismiss: () -> Unit, onSave: (FlatFeePreset) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var appliesTo by rememberSaveable { mutableStateOf(existing?.appliesTo ?: "TRAVEL") }
    var replacesHourly by rememberSaveable { mutableStateOf(existing?.replacesHourly ?: true) }
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
                StableTextField(name, { name = it.take(80) }, label = { Text(tr("Name", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectMenu(appliesTo, listOf("WORK", "TRAVEL", "ORDER"), label = { when(it) { "WORK" -> tr("Arbeitszeit", "Work time"); "TRAVEL" -> tr("Fahrtzeit", "Travel time"); else -> tr("Gesamter Auftrag", "Whole order") } }) { appliesTo = it }
                if (appliesTo != "ORDER") ToggleRow(tr("Ersetzt den Stundensatz dieser Zeitart", "Replaces hourly rate for this time category"), replacesHourly) { replacesHourly = it }
                MoneyField(tr("Pauschalbetrag", "Flat amount"), fixed, { fixed = it })
                Text(tr("Entfernungsstaffeln (Hin- und Rückweg zusammen)", "Distance bands (round trip total)"), style = MaterialTheme.typography.titleSmall)
                bands.sortedBy { it.upToKm }.forEach { band ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Bis ${band.upToKm.toInt()} km", "Up to ${band.upToKm.toInt()} km") + " · ${band.cents / 100.0} €", Modifier.weight(1f))
                        IconButton(onClick = { bands = bands - band }) { Icon(Icons.Outlined.Delete, tr("Entfernen", "Remove")) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StableTextField(km, { km = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) }, label = { Text(tr("Bis km", "Up to km")) }, singleLine = true, modifier = Modifier.weight(1f))
                    StableTextField(amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) }, label = { Text(tr("Betrag", "Amount")) }, suffix = { Text("€") }, singleLine = true, modifier = Modifier.weight(1f))
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
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(FlatFeePreset(existing?.id ?: UUID.randomUUID().toString(), name.trim(), eurosToCents(fixed)!!, bands, appliesTo, replacesHourly)) }) { Text(tr("Speichern", "Save")) } },
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
            Text(tr("Kalenderfarbe in KalPlan", "Calendar color in KalPlan"))
            LabelColorPicker(value.colorHex.ifBlank { calendar.colorArgb?.let { "%06X".format(it and 0xFFFFFF) } ?: "6750A4" }) { value = value.copy(colorHex = it) }
            ToggleRow(tr("Titel anzeigen", "Show title"), value.showTitle) { value = value.copy(showTitle = it) }
            ToggleRow(tr("Ort anzeigen", "Show location"), value.showLocation) { value = value.copy(showLocation = it) }
            ToggleRow(tr("Beschreibung anzeigen", "Show description"), value.showDescription) { value = value.copy(showDescription = it) }
            ToggleRow(tr("Als Fahrtenkalender verwenden", "Use as travel calendar"), value.travelCalendar) { value = value.copy(travelCalendar = it) }
            if (value.travelCalendar) StableTextField(
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
        Row(verticalAlignment = Alignment.CenterVertically) { StableTextField(mime, { mime = it.take(100) }, label = { Text(tr("Weiterer MIME-Typ", "Another MIME type")) }, singleLine = true, modifier = Modifier.weight(1f)); IconButton(enabled = mime.isNotBlank(), onClick = { selectedMimes = (selectedMimes + mime.trim()).distinct(); mime = "" }) { Icon(Icons.Outlined.Add, null) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { selectedMimes.forEach { value -> InputChip(selected = true, onClick = { selectedMimes = selectedMimes - value }, label = { Text(value) }, trailingIcon = { Text("×") }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { StableTextField(extension, { extension = it.removePrefix(".").take(20) }, label = { Text(tr("Weitere Dateiendung", "Another extension")) }, singleLine = true, modifier = Modifier.weight(1f)); IconButton(enabled = extension.isNotBlank(), onClick = { selectedExtensions = (selectedExtensions + extension.trim()).distinct(); extension = "" }) { Icon(Icons.Outlined.Add, null) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { selectedExtensions.forEach { value -> InputChip(selected = true, onClick = { selectedExtensions = selectedExtensions - value }, label = { Text(".$value") }, trailingIcon = { Text("×") }) } }
        StableTextField(name, { name = it.take(100) }, label = { Text(tr("Dateiname enthält", "Filename contains")) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StableTextField(min, { min = it.filter(Char::isDigit).take(9) }, label = { Text(tr("Min. KB", "Min KB")) }, modifier = Modifier.weight(1f)); StableTextField(max, { max = it.filter(Char::isDigit).take(9) }, label = { Text(tr("Max. KB", "Max KB")) }, modifier = Modifier.weight(1f)) }
        ChoiceRow(listOf("ANY", "true", "false"), inline, label = { when(it) { "true" -> tr("Eingebettet", "Inline"); "false" -> tr("Angehängt", "Attached"); else -> tr("Beides", "Either") } }) { inline = it }
    } }, confirmButton = { TextButton(onClick = { onSave(AttachmentRule(show = show, nameContains = name.trim(), minBytes = min.toIntOrNull()?.times(1024), maxBytes = max.toIntOrNull()?.times(1024), inline = inline.toBooleanStrictOrNull(), mimePrefixes = selectedMimes.take(20), extensions = selectedExtensions.take(20))) }) { Text(tr("Speichern", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } })
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
    "ONLINE" -> tr("Terminart", "Meeting mode"); "POSTAL_CODE" -> tr("Postleitzahl", "Postal code"); "CITY" -> tr("Stadt", "City")
    "STREET" -> tr("Straße und Hausnummer", "Street and number"); "CUSTOM" -> tr("Eigene Information", "Custom information")
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

