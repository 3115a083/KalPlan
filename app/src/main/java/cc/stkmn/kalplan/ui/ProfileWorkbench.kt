@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cc.stkmn.kalplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.stkmn.kalplan.data.StoredRequest
import cc.stkmn.kalplan.extraction.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

fun profileMailInputs(requests: List<StoredRequest>): List<Pair<String, ExtractionInput>> = requests.take(30).map {
    it.subject.take(100) to ExtractionInput(it.sender, it.subject, it.body, Instant.ofEpochMilli(it.receivedMillis), ZoneId.systemDefault())
}

private fun ProfileExample.input() = ExtractionInput(sender, subject, body, Instant.ofEpochMilli(receivedMillis), ZoneId.of(zoneId))
private fun ExtractionInput.text(source: InputSource): String = when (source) {
    InputSource.BODY -> body; InputSource.SUBJECT -> subject; InputSource.SENDER -> sender; InputSource.COMBINED -> combined
}

internal class ProfileWorkbenchState(existing: ExtractionProfile?, initial: ExtractionInput, fromMail: Boolean, exampleWord: String) {
    val profile = mutableStateOf(existing?.copy(schemaVersion = ExtractionProfile.CURRENT_SCHEMA_VERSION) ?: ExtractionProfile(id = UUID.randomUUID().toString(), name = initial.subject.take(80)))
    val input = mutableStateOf(initial)
    val exampleId = mutableStateOf(if (!fromMail) existing?.examples?.firstOrNull()?.id ?: UUID.randomUUID().toString() else UUID.randomUUID().toString())
    val exampleName = mutableStateOf(if (!fromMail) existing?.examples?.firstOrNull()?.name ?: exampleWord else exampleWord)
    val expected = mutableStateOf(if (!fromMail) existing?.examples?.firstOrNull()?.expected.orEmpty() else emptyMap())
    val shouldMatch = mutableStateOf(if (!fromMail) existing?.examples?.firstOrNull()?.shouldMatch ?: true else true)
    val received = mutableStateOf(initial.receivedAt.toString())
    val exampleZone = mutableStateOf(initial.zoneId.id)
    val tab = mutableIntStateOf(0)
    val selectedId = mutableStateOf(profile.value.extractors.firstOrNull()?.id)
    val sourceChoice = mutableStateOf("BODY")
    val addVariant = mutableStateOf(false)
    val selection = mutableStateOf(TextFieldValue(initial.body.take(20_000)))
    val proposals = mutableStateOf(emptyList<ExtractorRule>())
    val raw = mutableStateOf<ProfileExtractionEngine.ProfileExtraction?>(null)
    val preview = mutableStateOf<ExtractionResult?>(null)
    val previewBusy = mutableStateOf(false)
    val checks = mutableStateOf(emptyList<ProfileExampleCheck>())
    val checking = mutableStateOf(false)
    val feedback = mutableStateOf("")
    val duration = mutableStateOf(existing?.defaultDurationMinutes?.toString().orEmpty())
    val zone = mutableStateOf(existing?.extractionZoneId.orEmpty())
    val confirmDelete = mutableStateOf(false)
    val corrections = mutableStateOf(emptyMap<String, String>())
}

/** Mail-containing drafts stay in memory across activity recreation, outside SavedState bundles. */
class ProfileWorkbenchDraftStore : androidx.lifecycle.ViewModel() {
    internal val sessions = mutableMapOf<String, ProfileWorkbenchState>()
}

private fun android.content.Context.workbenchActivity(): androidx.activity.ComponentActivity? {
    var context: android.content.Context = this
    while (context is android.content.ContextWrapper) {
        if (context is androidx.activity.ComponentActivity) return context
        context = context.baseContext
    }
    return context as? androidx.activity.ComponentActivity
}

/** One local editor for settings and source-mail learning. No source mail is modified. */
@Composable
fun ProfileEditorDialog(
    existing: ExtractionProfile?, onDismiss: () -> Unit, onSave: (ExtractionProfile) -> Unit, onDelete: (() -> Unit)?,
    labels: List<String> = emptyList(), initialInput: ExtractionInput? = null,
    availableInputs: List<Pair<String, ExtractionInput>> = emptyList()
) {
    val exampleWord = tr("Beispiel", "Example")
    val fieldWord = tr("Feld", "Field")
    val firstInput = remember(existing?.id) {
        initialInput ?: existing?.examples?.firstOrNull()?.let { runCatching { it.input() }.getOrNull() }
            ?: availableInputs.firstOrNull()?.second ?: ExtractionInput("", "", "", Instant.now(), ZoneId.systemDefault())
    }
    val activity = androidx.compose.ui.platform.LocalContext.current.workbenchActivity()
    val store = remember(activity) { activity?.let { androidx.lifecycle.ViewModelProvider(it)[ProfileWorkbenchDraftStore::class.java] } }
    val sessionKey = rememberSaveable { UUID.randomUUID().toString() }
    val state = remember(sessionKey) {
        store?.sessions?.getOrPut(sessionKey) { ProfileWorkbenchState(existing, firstInput, initialInput != null, exampleWord) }
            ?: ProfileWorkbenchState(existing, firstInput, initialInput != null, exampleWord)
    }
    var profile by state.profile
    var input by state.input
    var exampleId by state.exampleId
    var exampleName by state.exampleName
    var expected by state.expected
    var shouldMatch by state.shouldMatch
    var received by state.received
    var exampleZone by state.exampleZone
    var tab by state.tab
    var selectedId by state.selectedId
    var sourceChoice by state.sourceChoice
    var addVariant by state.addVariant
    var selection by state.selection
    var proposals by state.proposals
    var raw by state.raw
    var preview by state.preview
    var previewBusy by state.previewBusy
    var checks by state.checks
    var checking by state.checking
    var feedback by state.feedback
    var duration by state.duration
    var zone by state.zone
    var confirmDelete by state.confirmDelete
    var corrections by state.corrections
    val close = { store?.sessions?.remove(sessionKey); onDismiss() }
    val scope = rememberCoroutineScope()
    val currentRule = profile.extractors.firstOrNull { it.id == selectedId }
    val receivedValid = runCatching { Instant.parse(received) }.isSuccess
    val exampleZoneValid = runCatching { ZoneId.of(exampleZone) }.isSuccess
    val profileZoneValid = zone.isBlank() || runCatching { ZoneId.of(zone) }.isSuccess
    val validDuration = duration.isBlank() || duration.toIntOrNull()?.let { it in 1..10080 } == true
    val errors = remember(profile) { ProfileValidator().validate(profile) }
    val valid = errors.isEmpty() && validDuration && profileZoneValid

    fun changeRule(rule: ExtractorRule) { profile = profile.copy(extractors = profile.extractors.map { if (it.id == rule.id) rule else it }) }
    fun loadExample(value: ExtractionInput, id: String = UUID.randomUUID().toString(), name: String = exampleWord, values: Map<String, String> = emptyMap(), match: Boolean = true) {
        input = value; exampleId = id; exampleName = name; expected = values; shouldMatch = match
        received = value.receivedAt.toString(); exampleZone = value.zoneId.id; proposals = emptyList(); feedback = ""
    }
    fun saveExample(values: Map<String, String> = expected) {
        if (!receivedValid || !exampleZoneValid || exampleName.isBlank() || input.sender.length + input.subject.length + input.body.length > 20_000 || profile.examples.size >= 10 && profile.examples.none { it.id == exampleId }) {
            feedback = "example_limit"; return
        }
        val saved = ProfileExample(exampleId, exampleName.take(100), input.sender, input.subject, input.body,
            input.receivedAt.toEpochMilli(), input.zoneId.id, values, shouldMatch)
        expected = values
        profile = profile.copy(examples = profile.examples.filterNot { it.id == saved.id } + saved)
        feedback = "example_saved"
    }
    fun selectSourceFor(rule: ExtractorRule?) {
        sourceChoice = rule?.sourceVariableKey?.let { "var:$it" } ?: rule?.source?.name ?: "BODY"
    }
    fun addFromSelection(replace: Boolean) {
        try {
            val key = if (replace) requireNotNull(currentRule).key else generateSequence(1) { it + 1 }.map { "field_$it" }.first { k -> profile.extractors.none { it.key == k } }
            val source = InputSource.entries.firstOrNull { it.name == sourceChoice } ?: InputSource.BODY
            val variable = sourceChoice.takeIf { it.startsWith("var:") }?.removePrefix("var:")
            if (replace && addVariant) {
                val old = requireNotNull(currentRule)
                if (old.sourceVariableKey?.takeIf(String::isNotBlank) != variable || variable == null && old.source != source) {
                    feedback = "variant_source"; return
                }
            }
            val selector = FieldRuleBuilder.fromSelection(selection.text, selection.selection.min, selection.selection.max, source, key)
            if (replace) {
                val old = requireNotNull(currentRule)
                changeRule(if (addVariant) old.copy(variants = (old.variants + selector).take(8)) else old.copy(source = source, sourceVariableKey = variable, selector = selector))
            } else {
                val rule = ExtractorRule(UUID.randomUUID().toString(), key, regex = "", source = source, selector = selector, sourceVariableKey = variable, displayName = "$fieldWord ${profile.extractors.size + 1}")
                profile = profile.copy(extractors = profile.extractors + rule); selectedId = rule.id
            }
            addVariant = false; tab = 1; feedback = ""
        } catch (_: Exception) { feedback = "selection_invalid" }
    }
    LaunchedEffect(profile, input, receivedValid, exampleZoneValid) {
        preview = null; raw = null; previewBusy = true; checks = emptyList()
        delay(250)
        try {
            if (receivedValid && exampleZoneValid) {
                val result = withContext(Dispatchers.Default) { ProfileExtractionEngine().extract(MailTextNormalizer().normalize(input), profile) to ProfilePreview.evaluate(input, profile) }
                raw = result.first; preview = result.second
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { feedback = "preview_failed" }
        finally { previewBusy = false }
    }
    val normalizedInput = remember(input) { MailTextNormalizer().normalize(input) }
    val sourceText = if (sourceChoice.startsWith("var:")) {
        val key = sourceChoice.removePrefix("var:")
        raw?.values?.get(key)?.value ?: when (key) { "body", "text" -> normalizedInput.body; "sender" -> normalizedInput.sender; "subject" -> normalizedInput.subject; "input" -> normalizedInput.combined; else -> "" }
    } else if (sourceChoice == "COMBINED") normalizedInput.combined else input.text(InputSource.valueOf(sourceChoice))
    SideEffect {
        val shown = sourceText.take(20_000)
        if (selection.text != shown) selection = selection.copy(text = shown,
            selection = TextRange(selection.selection.start.coerceIn(0, shown.length), selection.selection.end.coerceIn(0, shown.length)), composition = null)
    }
    CompositionLocalProvider(androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides null) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        cc.stkmn.kalplan.ui.theme.SyncSystemBars()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Text(tr("Profilwerkstatt", "Profile workbench"), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    listOf(tr("Beispiel", "Example"), tr("Felder", "Fields"), tr("Vorschau", "Preview"), tr("Profil", "Profile")).forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, modifier = Modifier.testTag("profile-tab-$index"), text = { Text(title) })
                    }
                }
                key(tab) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (feedback.isNotBlank()) Text(when (feedback) {
                            "example_saved" -> tr("Beispiel mit Sollwerten gespeichert. Die Erkennungsregel wurde dadurch nicht verändert.", "Example and expected values saved. This did not change the extraction rule.")
                            "example_limit" -> tr("Maximal 10 Beispiele mit jeweils insgesamt 20.000 Zeichen. Namen, Empfangszeit und Zeitzone prüfen.", "At most 10 examples, each totaling 20,000 characters. Check name, received time and time zone.")
                            "selection_invalid" -> tr("Die Markierung hat keinen eindeutigen Kontext. Wähle den Wert mit klaren Grenzen oder bearbeite die Regel unter Felder.", "The selection has no unambiguous context. Select a value with clear boundaries or edit its rule under Fields.")
                            "variant_source" -> tr("Eine Alternative muss dieselbe Quelle wie das Feld verwenden. Wähle die ursprüngliche Quelle oder erstelle ein eigenes Feld.", "An alternative must use the same source as its field. Choose the original source or create a separate field.")
                            else -> tr("Vorschau nicht verfügbar. Feldregeln und Beispiel prüfen.", "Preview unavailable. Check the field rules and example.")
                        }, style = MaterialTheme.typography.bodySmall)
                        when (tab) {
                            0 -> {
                                Text(tr("Wert oder Abschnitt im Beispiel markieren und daraus ein frei benanntes Feld erstellen.", "Select a value or section in the example to create a named field."))
                                if (availableInputs.isNotEmpty()) SelectMenu("", listOf("") + availableInputs.indices.map(Int::toString), label = { value -> if (value.isBlank()) tr("Mail aus lokalen Anfragen wählen", "Choose mail from local requests") else availableInputs[value.toInt()].first }) { value ->
                                    value.toIntOrNull()?.let { index -> loadExample(availableInputs[index].second, name = availableInputs[index].first.take(100)) }
                                }
                                OutlinedButton(onClick = { loadExample(ExtractionInput("", "", "", Instant.now(), ZoneId.systemDefault())) }) { Text(tr("Neues Beispiel einfügen", "Paste a new example")) }
                                if (profile.examples.isNotEmpty()) SelectMenu("", listOf("") + profile.examples.map { it.id }, label = { id -> profile.examples.firstOrNull { it.id == id }?.name ?: tr("Gespeichertes Beispiel wählen", "Choose a saved example") }) { id ->
                                    profile.examples.firstOrNull { it.id == id }?.let { saved -> loadExample(saved.input(), saved.id, saved.name, saved.expected, saved.shouldMatch) }
                                }
                                WorkText(tr("Beispielname", "Example name"), exampleName) { exampleName = it.take(100) }
                                WorkText(tr("Absender", "Sender"), input.sender) { input = input.copy(sender = it.take(512)) }
                                WorkText(tr("Betreff", "Subject"), input.subject) { input = input.copy(subject = it.take(2000)) }
                                SelectMenu(sourceChoice, InputSource.entries.map { it.name } + profile.extractors.map { "var:${it.key}" }, label = { workbenchSourceName(it) }) { sourceChoice = it }
                                OutlinedTextField(selection, { next ->
                                    selection = next.copy(text = next.text.take(20_000))
                                    if (next.text != sourceText && !sourceChoice.startsWith("var:") && sourceChoice != "COMBINED") input = when (sourceChoice) {
                                        "SENDER" -> input.copy(sender = selection.text); "SUBJECT" -> input.copy(subject = selection.text); else -> input.copy(body = selection.text)
                                    }
                                }, modifier = Modifier.fillMaxWidth().testTag("profile-source"), readOnly = sourceChoice.startsWith("var:") || sourceChoice == "COMBINED", minLines = 7, maxLines = 14,
                                    label = { Text(tr("Quelltext, hier markieren", "Source text, select here")) })
                                if (sourceText.length > 20_000) Text(tr("Es werden die ersten 20.000 Zeichen angezeigt. Kürze ein ausdrücklich gespeichertes Beispiel auf insgesamt 20.000 Zeichen.", "Showing the first 20,000 characters. Explicitly saved examples must total at most 20,000 characters."), style = MaterialTheme.typography.bodySmall)
                                Text(tr("Markierung: ", "Selection: ") + "${selection.selection.min}…${selection.selection.max}", style = MaterialTheme.typography.labelSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !selection.selection.collapsed && profile.extractors.size < 100, onClick = { addFromSelection(false) }) { Text(tr("Markierung als neues Feld", "Create field from selection")) }
                                    if (currentRule != null) OutlinedButton(enabled = !selection.selection.collapsed && (!addVariant || currentRule.variants.size < 8), onClick = { addFromSelection(true) }) { Text(if (addVariant) tr("Alternative übernehmen", "Use alternative") else tr("Markierung für gewähltes Feld", "Use selection for selected field")) }
                                }
                                OutlinedButton(onClick = { proposals = FieldRuleBuilder.suggestions(input).take(100) }) { Text(tr("Felder vorschlagen", "Suggest fields")) }
                                proposals.forEach { proposed ->
                                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                        Text(proposed.displayName.ifBlank { proposed.key })
                                        TextButton(enabled = profile.extractors.size < 100 && profile.extractors.none { it.key == proposed.key }, onClick = { profile = profile.copy(extractors = profile.extractors + proposed); selectedId = proposed.id }) { Text(tr("Feld hinzufügen", "Add field")) }
                                    } }
                                }
                                HorizontalDivider()
                                WorkText(tr("Empfangen, ISO-Zeit mit Z oder Offset", "Received, ISO time with Z or offset"), received, !receivedValid) { value -> received = value; runCatching { Instant.parse(value) }.getOrNull()?.let { input = input.copy(receivedAt = it) } }
                                WorkText(tr("Beispielzeitzone", "Example time zone"), exampleZone, !exampleZoneValid) { value -> exampleZone = value; runCatching { ZoneId.of(value) }.getOrNull()?.let { input = input.copy(zoneId = it) } }
                                WorkToggle(tr("Profil soll auf dieses Beispiel passen", "Profile should match this example"), shouldMatch) { shouldMatch = it }
                                Text(tr("Beispiele werden nur mit dieser ausdrücklichen Aktion im Profil gespeichert. Sie enthalten Mailtext und werden in Profilsicherungen eingeschlossen.", "Examples are stored in the profile only with this explicit action. They contain mail text and are included in profile backups."), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { saveExample() }) { Text(tr("Beispiel speichern", "Save example")) }
                                if (profile.examples.any { it.id == exampleId }) TextButton(onClick = { profile = profile.copy(examples = profile.examples.filterNot { it.id == exampleId }) }) { Text(tr("Gespeichertes Beispiel entfernen", "Remove saved example")) }
                            }
                            1 -> {
                                if (profile.extractors.isEmpty()) Text(tr("Erstelle ein Feld aus einer Markierung oder füge es frei hinzu.", "Create a field from a selection or add one manually."))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { profile.extractors.forEach { rule -> FilterChip(selected = rule.id == selectedId, onClick = { selectedId = rule.id; selectSourceFor(rule) }, label = { Text(rule.displayName.ifBlank { rule.key }) }) } }
                                OutlinedButton(enabled = profile.extractors.size < 100, onClick = {
                                    val key = generateSequence(1) { it + 1 }.map { "field_$it" }.first { k -> profile.extractors.none { it.key == k } }
                                    val rule = ExtractorRule(UUID.randomUUID().toString(), key, regex = "", selector = FieldSelector(), displayName = "$fieldWord ${profile.extractors.size + 1}")
                                    profile = profile.copy(extractors = profile.extractors + rule); selectedId = rule.id
                                }) { Text(tr("Freies Feld hinzufügen", "Add custom field")) }
                                currentRule?.let { rule -> key(rule.id) {
                                    ProfileFieldEditor(rule, profile.extractors.takeWhile { it.id != rule.id }.map { it.key }, labels, onChange = ::changeRule)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { selectSourceFor(rule); addVariant = false; tab = 0 }) { Text(tr("Quelle neu markieren", "Reselect source")) }
                                        OutlinedButton(enabled = rule.variants.size < 8, onClick = { selectSourceFor(rule); addVariant = true; tab = 0 }) { Text(tr("Alternative markieren", "Select alternative")) }
                                        TextButton(onClick = { profile = profile.copy(extractors = profile.extractors.filterNot { it.id == rule.id }); selectedId = profile.extractors.firstOrNull()?.id }) { Text(tr("Feld entfernen", "Remove field")) }
                                        val index = profile.extractors.indexOfFirst { it.id == rule.id }
                                        TextButton(enabled = index > 0, onClick = { val rules = profile.extractors.toMutableList(); java.util.Collections.swap(rules, index, index - 1); profile = profile.copy(extractors = rules) }) { Text(tr("Nach oben", "Move up")) }
                                        TextButton(enabled = index < profile.extractors.lastIndex, onClick = { val rules = profile.extractors.toMutableList(); java.util.Collections.swap(rules, index, index + 1); profile = profile.copy(extractors = rules) }) { Text(tr("Nach unten", "Move down")) }
                                    }
                                } }
                            }
                            2 -> {
                                Text(tr("Die Vorschau verwendet dieselbe Verarbeitung wie eingehende Mails.", "Preview uses the same processing as incoming mail."))
                                if (previewBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (profile.extractors.isNotEmpty()) SelectMenu(selectedId.orEmpty(), profile.extractors.map { it.id }, label = { id -> profile.extractors.firstOrNull { it.id == id }?.let { it.displayName.ifBlank { it.key } }.orEmpty() }) { selectedId = it }
                                currentRule?.let { rule ->
                                    val value = raw?.values?.get(rule.key)
                                    Text(tr("Profilregel: ", "Profile rule: ") + when { value == null -> tr("nicht gefunden", "not found"); value.value.isEmpty() -> tr("Feld vorhanden, aber leer", "field found but empty"); else -> value.value }, Modifier.testTag("profile-raw-match"))
                                    value?.let { Text(tr("Rohfund: ", "Raw match: ") + it.evidence.matchedText, style = MaterialTheme.typography.bodySmall) }
                                    raw?.issues?.filter { it.fieldKey == rule.key }?.forEach { Text(it.message, color = MaterialTheme.colorScheme.error) }
                                    val correctionKey = exampleId + ":" + rule.id
                                    val correction = corrections[correctionKey] ?: expected[rule.key] ?: value?.value.orEmpty()
                                    WorkText(tr("Erwarteter Wert für dieses Beispiel", "Expected value for this example"), correction, modifier = Modifier.testTag("profile-expected")) { corrections = corrections + (correctionKey to it.take(20_000)) }
                                    Text(tr("Der Sollwert ist der extrahierte Text vor der Datumsinterpretation. Er ändert die Erkennung nicht. Verfeinere die Quelle oder Regel und teste erneut.", "The expected value is the extracted text before date interpretation. It does not change extraction. Refine the source or rule and test again."), style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { saveExample(expected + (rule.key to correction)) }, modifier = Modifier.testTag("profile-save-expected")) { Text(tr("Sollwert im Beispiel speichern", "Save expected value in example")) }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { selectSourceFor(rule); addVariant = false; tab = 0 }) { Text(tr("Quelle neu markieren", "Reselect source")) }
                                        TextButton(onClick = { tab = 1 }) { Text(tr("Regel verfeinern", "Refine rule")) }
                                        TextButton(enabled = rule.variants.size < 8, onClick = { selectSourceFor(rule); addVariant = true; tab = 0 }) { Text(tr("Alternative markieren", "Select alternative")) }
                                    }
                                }
                                preview?.let { result ->
                                    Text(tr("Verarbeitete Felder und Evidenz", "Processed fields and evidence"), style = MaterialTheme.typography.titleMedium)
                                    result.fields.values.forEach { value -> Text("${value.key}: ${value.value}\n${evidenceName(value.evidence.kind)} · ${(value.evidence.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) }
                                    Text(tr("Terminvorschläge", "Appointment candidates"), style = MaterialTheme.typography.titleMedium)
                                    result.candidates.forEach { candidate -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(candidate.start?.atZone(profile.extractionZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: input.zoneId)?.toString() ?: tr("Datum oder Uhrzeit fehlt", "Date or time missing"))
                                        Text(candidate.locationText.orEmpty().ifBlank { tr("Ort fehlt", "Location missing") })
                                        Text("${candidate.mode} · ${(candidate.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                        candidate.warnings.forEach { Text(reasonText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                    } } }
                                    if (result.labels.isNotEmpty()) Text(tr("Labels: ", "Labels: ") + result.labels.joinToString { it.name })
                                    result.issues.forEach { Text(it.message, color = if (it.severity == IssueSeverity.NEEDS_REVIEW) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                                }
                                OutlinedButton(enabled = !checking && profile.examples.isNotEmpty(), modifier = Modifier.testTag("profile-check-examples"), onClick = { val checkedProfile = profile; scope.launch {
                                    checking = true
                                    try { val results = withContext(Dispatchers.Default) { ProfilePreview.checkExamples(checkedProfile) }; checks = if (profile == checkedProfile) results else emptyList() }
                                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (_: Exception) { feedback = "preview_failed" }
                                    finally { checking = false }
                                } }) { Text(tr("Alle gespeicherten Beispiele testen", "Test all saved examples")) }
                                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                                checks.forEachIndexed { index, check -> Text((if (check.passed) "✓ " else "! ") + check.name + "\n" + check.details.joinToString("\n"), modifier = Modifier.testTag("profile-example-check-$index"), color = if (check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                            }
                            3 -> {
                                WorkText(tr("Profilname", "Profile name"), profile.name) { profile = profile.copy(name = it.take(100)) }
                                WorkToggle(tr("Profil aktiv", "Profile enabled"), profile.enabled) { profile = profile.copy(enabled = it) }
                                WorkText(tr("Optionale Standarddauer, Minuten", "Optional default duration, minutes"), duration, !validDuration) { duration = it.filter(Char::isDigit).take(5); profile = profile.copy(defaultDurationMinutes = duration.toIntOrNull()) }
                                WorkText(tr("Profilzeitzone, leer = Gerätezeitzone", "Profile time zone, empty = device time zone"), zone, !profileZoneValid) { zone = it.take(80); profile = profile.copy(extractionZoneId = it.takeIf(String::isNotBlank)) }
                                Text(tr("Gerätezeitzone: ", "Device time zone: ") + ZoneId.systemDefault().id, style = MaterialTheme.typography.bodySmall)
                                SelectMenu(profile.parseDirection.name, ParseDirection.entries.map { it.name }, label = { if (it == "TOP_DOWN") tr("Von oben nach unten", "Top to bottom") else tr("Von unten nach oben", "Bottom to top") }) { profile = profile.copy(parseDirection = ParseDirection.valueOf(it)) }
                                SelectMenu(profile.locale.name, TemporalLocale.entries.map { it.name }, label = { if (it == "DE_DE") "Deutsch (DE)" else "English (US)" }) { profile = profile.copy(locale = TemporalLocale.valueOf(it)) }
                                SelectMenu(profile.multipleDateMode.name, MultipleDateMode.entries.map { it.name }, label = { multipleDateName(it) }) { profile = profile.copy(multipleDateMode = MultipleDateMode.valueOf(it)) }
                                Text(tr("Antwortvorlagen können freie Felder als {feld.key} einsetzen.", "Reply templates can insert custom fields as {feld.key}."), style = MaterialTheme.typography.bodySmall)
                                StableTextField(profile.acceptTemplate.orEmpty(), { profile = profile.copy(acceptTemplate = it.take(100_000).takeIf(String::isNotBlank)) }, label = { Text(tr("Annahmevorlage, leer = global", "Acceptance template, empty = global")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                                StableTextField(profile.declineTemplate.orEmpty(), { profile = profile.copy(declineTemplate = it.take(100_000).takeIf(String::isNotBlank)) }, label = { Text(tr("Absagevorlage, leer = global", "Decline template, empty = global")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                                Text(tr("Profilzuordnung, erweiterte Muster", "Profile matching, advanced patterns"), style = MaterialTheme.typography.titleMedium)
                                profile.matchers.forEach { matcher -> key(matcher.id) {
                                    WorkText(tr("Suchmuster (Regex)", "Match pattern (regex)"), matcher.regex) { text -> profile = profile.copy(matchers = profile.matchers.map { if (it.id == matcher.id) matcher.copy(regex = text.take(4000)) else it }) }
                                    SelectMenu(matcher.source.name, InputSource.entries.map { it.name }, label = { workbenchSourceName(it) }) { source -> profile = profile.copy(matchers = profile.matchers.map { if (it.id == matcher.id) matcher.copy(source = InputSource.valueOf(source)) else it }) }
                                    WorkToggle(tr("Groß-/Kleinschreibung ignorieren", "Ignore case"), matcher.ignoreCase) { checked -> profile = profile.copy(matchers = profile.matchers.map { if (it.id == matcher.id) matcher.copy(ignoreCase = checked) else it }) }
                                    TextButton(onClick = { profile = profile.copy(matchers = profile.matchers.filterNot { it.id == matcher.id }) }) { Text(tr("Muster entfernen", "Remove pattern")) }
                                } }
                                OutlinedButton(enabled = profile.matchers.size < 100, onClick = { profile = profile.copy(matchers = profile.matchers + MatcherRule(UUID.randomUUID().toString(), "")) }) { Text(tr("Zuordnungsmuster hinzufügen", "Add matching pattern")) }
                                Text(tr("Ohne Zuordnungsmuster ist das Profil allgemein. Die Ordnerzuordnung erfolgt in den Mailkontoeinstellungen.", "Without matching patterns the profile is general. Assign mail folders in account settings."), style = MaterialTheme.typography.bodySmall)
                                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(tr("Profil löschen", "Delete profile")) }
                            }
                        }
                        if (errors.isNotEmpty()) errors.take(12).forEach { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                HorizontalDivider()
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = close, modifier = Modifier.weight(1f)) { Text(tr("Abbrechen", "Cancel")) }
                        Button(enabled = valid, onClick = { store?.sessions?.remove(sessionKey); onSave(profile.copy(name = profile.name.trim())) }, modifier = Modifier.weight(1f).testTag("profile-save")) { Text(tr("Speichern", "Save")) }
                    }
                }
            }
        }
    }
    }
    if (confirmDelete && onDelete != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("Profil löschen?", "Delete profile?")) }, text = { Text(profile.name) },
        confirmButton = { TextButton(onClick = { store?.sessions?.remove(sessionKey); onDelete() }) { Text(tr("Löschen", "Delete")) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen", "Cancel")) } })
}

@Composable
private fun WorkText(label: String, value: String, error: Boolean = false, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    StableTextField(value, onChange, label = { Text(label) }, singleLine = true, isError = error, modifier = modifier.fillMaxWidth())
}

@Composable
private fun WorkToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = value, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp)); Switch(value, onCheckedChange = null)
    }
}

@Composable
private fun ProfileFieldEditor(rule: ExtractorRule, precedingKeys: List<String>, labels: List<String>, onChange: (ExtractorRule) -> Unit) {
    WorkText(tr("Feldname", "Field name"), rule.displayName) { onChange(rule.copy(displayName = it.take(100))) }
    Text("{feld.${rule.key}}", style = MaterialTheme.typography.labelLarge)
    SelectMenu(rule.dataType.name, ProfileFieldType.entries.map { it.name }, label = { fieldTypeName(it) }) { onChange(rule.copy(dataType = ProfileFieldType.valueOf(it))) }
    SelectMenu(rule.sourceVariableKey?.let { "var:$it" } ?: rule.source.name,
        InputSource.entries.map { it.name } + (listOf("input", "body", "text", "subject", "sender") + precedingKeys).distinct().map { "var:$it" }, label = { workbenchSourceName(it) }) { value ->
        onChange(if (value.startsWith("var:")) rule.copy(sourceVariableKey = value.removePrefix("var:")) else rule.copy(source = InputSource.valueOf(value), sourceVariableKey = null))
    }
    WorkToggle(tr("Pflichtfeld", "Required field"), rule.required) { onChange(rule.copy(required = it)) }
    WorkToggle(tr("Bei fehlendem Treffer im Mailtext suchen", "Search mail text when no match is found"), rule.allowHeuristicFallback) { onChange(rule.copy(allowHeuristicFallback = it)) }
    Text(tr("Ein ausdrücklich leeres Feld bleibt leer. Eine Suche bei fehlendem Treffer zeigt Hinweise und Unsicherheit in der Vorschau.", "An explicitly empty field stays empty. Searching after a missing match shows evidence and uncertainty in Preview."), style = MaterialTheme.typography.bodySmall)
    val selector = rule.selector ?: FieldSelector(kind = FieldSelectorKind.REGEX, pattern = rule.regex, group = rule.group, ignoreCase = false)
    ProfileSelectorEditor(selector) { next -> onChange(rule.copy(selector = next)) }
    rule.variants.forEachIndexed { index, variant ->
        Text(tr("Alternative ", "Alternative ") + (index + 1), style = MaterialTheme.typography.titleSmall)
        ProfileSelectorEditor(variant) { next -> onChange(rule.copy(variants = rule.variants.mapIndexed { i, old -> if (i == index) next else old })) }
        TextButton(onClick = { onChange(rule.copy(variants = rule.variants.filterIndexed { i, _ -> i != index })) }) { Text(tr("Alternative entfernen", "Remove alternative")) }
    }
    OutlinedButton(enabled = rule.variants.size < 8, onClick = { onChange(rule.copy(variants = rule.variants + FieldSelector())) }) { Text(tr("Alternative Regel hinzufügen", "Add alternative rule")) }
    Text(tr("Verwendung des Feldes", "Use this field for"), style = MaterialTheme.typography.titleMedium)
    SelectMenu(rule.semantic.name, SemanticField.entries.map { it.name }, label = { semanticName(it) }) { onChange(rule.copy(semantic = SemanticField.valueOf(it))) }
    Text(tr("Zusätzliche Verwendungen", "Additional uses"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SemanticField.entries.filter { it != SemanticField.CUSTOM && it != rule.semantic }.forEach { semantic ->
        FilterChip(selected = semantic in rule.additionalSemantics, onClick = { onChange(rule.copy(additionalSemantics = if (semantic in rule.additionalSemantics) rule.additionalSemantics - semantic else rule.additionalSemantics + semantic)) }, label = { Text(semanticName(semantic.name)) })
    } }
    WorkToggle(tr("In Beschreibung aufnehmen", "Include in description"), rule.includeInDescription) { onChange(rule.copy(includeInDescription = it)) }
    Text(tr("Wert einem bestehenden Label zuordnen", "Map a value to an existing label"), style = MaterialTheme.typography.titleSmall)
    rule.labelMappings.forEachIndexed { index, mapping ->
        WorkText(tr("Wenn der Wert gleich ist", "When value equals"), mapping.value) { value -> onChange(rule.copy(labelMappings = rule.labelMappings.mapIndexed { i, old -> if (i == index) old.copy(value = value.take(300)) else old })) }
        SelectMenu(mapping.label, (labels + mapping.label).distinct(), label = { it }) { label -> onChange(rule.copy(labelMappings = rule.labelMappings.mapIndexed { i, old -> if (i == index) old.copy(label = label) else old })) }
        TextButton(onClick = { onChange(rule.copy(labelMappings = rule.labelMappings.filterIndexed { i, _ -> i != index })) }) { Text(tr("Labelzuordnung entfernen", "Remove label mapping")) }
    }
    OutlinedButton(enabled = labels.isNotEmpty() && rule.labelMappings.size < 20, onClick = { onChange(rule.copy(labelMappings = rule.labelMappings + FieldLabelMapping("", labels.first()))) }) { Text(tr("Labelzuordnung hinzufügen", "Add label mapping")) }
    if (labels.isEmpty()) Text(tr("Lege Labels zuerst in den Einstellungen an.", "Create labels in settings first."), style = MaterialTheme.typography.bodySmall)
    Text(tr("Wert einer Terminart zuordnen", "Map a value to an appointment mode"), style = MaterialTheme.typography.titleSmall)
    var duplicateModeValue by remember(rule.id) { mutableStateOf(false) }
    if (duplicateModeValue) Text(tr("Dieser Wert ist bereits zugeordnet. Wähle einen anderen Wert.", "This value already has a mapping. Choose another value."), color = MaterialTheme.colorScheme.error)
    rule.modeMappings.entries.forEachIndexed { index, mapping ->
        WorkText(tr("Wert der Terminart", "Appointment-mode value"), mapping.key) { value ->
            val nextKey = value.take(100)
            duplicateModeValue = rule.modeMappings.keys.withIndex().any { (i, key) -> i != index && key == nextKey }
            if (!duplicateModeValue) {
                val entries = rule.modeMappings.entries.mapIndexed { i, old -> (if (i == index) nextKey else old.key) to old.value }
                onChange(rule.copy(modeMappings = entries.toMap()))
            }
        }
        SelectMenu(mapping.value, listOf("ONSITE", "ONLINE", "HYBRID", "UNKNOWN"), label = { modeName(it) }) { value -> onChange(rule.copy(modeMappings = rule.modeMappings + (mapping.key to value))) }
        TextButton(onClick = { onChange(rule.copy(modeMappings = rule.modeMappings - mapping.key)) }) { Text(tr("Terminartzuordnung entfernen", "Remove mode mapping")) }
    }
    OutlinedButton(enabled = rule.modeMappings.size < 20 && "" !in rule.modeMappings, onClick = { onChange(rule.copy(modeMappings = rule.modeMappings + ("" to "UNKNOWN"))) }) { Text(tr("Terminartzuordnung hinzufügen", "Add mode mapping")) }
}

@Composable
private fun ProfileSelectorEditor(value: FieldSelector, onChange: (FieldSelector) -> Unit) {
    SelectMenu(value.kind.name, FieldSelectorKind.entries.map { it.name }, label = { when (it) {
        "LABELED_LINE" -> tr("Wert nach Beschriftung", "Value after label")
        "BETWEEN" -> tr("Abschnitt zwischen Grenzen", "Section between anchors")
        "WHOLE" -> tr("Gesamte Quelle", "Whole source")
        "TABLE_COLUMN" -> tr("Tabellenspalte", "Table column")
        else -> tr("Regex, erweitert", "Regex, advanced")
    } }) { onChange(value.copy(kind = FieldSelectorKind.valueOf(it))) }
    when (value.kind) {
        FieldSelectorKind.LABELED_LINE -> {
            WorkText(tr("Beschriftung", "Label text"), value.anchor) { onChange(value.copy(anchor = it.take(1000))) }
            WorkText(tr("Trennzeichen", "Separator"), value.separator) { onChange(value.copy(separator = it.take(20))) }
        }
        FieldSelectorKind.BETWEEN -> {
            WorkText(tr("Text vor dem Abschnitt", "Text before section"), value.anchor) { onChange(value.copy(anchor = it.take(1000))) }
            WorkText(tr("Text nach dem Abschnitt", "Text after section"), value.endAnchor) { onChange(value.copy(endAnchor = it.take(1000))) }
        }
        FieldSelectorKind.TABLE_COLUMN -> {
            WorkText(tr("Spaltenüberschrift", "Column header"), value.anchor) { onChange(value.copy(anchor = it.take(1000))) }
            WorkText(tr("Spaltentrenner", "Column separator"), value.separator) { onChange(value.copy(separator = it.take(20))) }
            Text(tr("HTML-Tabellen: \\t als Spaltentrenner", "HTML tables: use \\t as column separator"), style = MaterialTheme.typography.bodySmall)
            var column by remember { mutableStateOf(value.column.toString()) }
            WorkText(tr("Spaltennummer, erste Spalte = 1", "Column number, first column = 1"), column, column.toIntOrNull()?.let { it in 1..100 } != true) { column = it.filter(Char::isDigit).take(3); onChange(value.copy(column = column.toIntOrNull() ?: 0)) }
        }
        FieldSelectorKind.REGEX -> {
            StableTextField(value.pattern, { onChange(value.copy(pattern = it.take(4000))) }, label = { Text(tr("Regulärer Ausdruck", "Regular expression")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            var group by remember { mutableStateOf(value.group.toString()) }
            WorkText(tr("Erfassungsgruppe, 0 = gesamter Treffer", "Capture group, 0 = whole match"), group) { group = it.filter(Char::isDigit).take(3); onChange(value.copy(group = group.toIntOrNull() ?: 0)) }
        }
        FieldSelectorKind.WHOLE -> Text(tr("Das vollständige gewählte Quellfeld wird übernommen.", "Uses the entire selected source field."), style = MaterialTheme.typography.bodySmall)
    }
    if (value.kind != FieldSelectorKind.WHOLE) WorkToggle(tr("Groß-/Kleinschreibung ignorieren", "Ignore case"), value.ignoreCase) { onChange(value.copy(ignoreCase = it)) }
}

@Composable
private fun workbenchSourceName(value: String): String = when (value) {
    "BODY" -> tr("Mailtext", "Mail body"); "SUBJECT" -> tr("Betreff", "Subject"); "SENDER" -> tr("Absender", "Sender"); "COMBINED" -> tr("Betreff und Mailtext", "Subject and body")
    else -> tr("Feldvariable: ", "Field variable: ") + value.removePrefix("var:")
}
@Composable
private fun fieldTypeName(value: String): String = when (value) {
    "TEXT" -> tr("Text", "Text"); "SECTION" -> tr("Mehrzeiliger Abschnitt", "Multiline section"); "DATE" -> tr("Datum", "Date"); "TIME" -> tr("Uhrzeit", "Time"); "DURATION" -> tr("Dauer", "Duration"); "NUMBER" -> tr("Zahl", "Number"); else -> tr("E-Mail-Adresse", "Email address")
}
@Composable
private fun modeName(value: String): String = when (value) { "ONSITE" -> tr("Vor Ort", "On site"); "ONLINE" -> tr("Online", "Online"); "HYBRID" -> tr("Hybrid", "Hybrid"); else -> tr("Ungeklärt", "Unknown") }
@Composable
private fun evidenceName(value: EvidenceKind): String = when (value) {
    EvidenceKind.PROFILE_RULE -> tr("Profilregel", "Profile rule"); EvidenceKind.HEURISTIC -> tr("Heuristik, bitte prüfen", "Heuristic, review required"); EvidenceKind.INFERRED_DEFAULT -> tr("Abgeleiteter Standard", "Inferred default"); else -> value.name
}

@Composable
private fun multipleDateName(value: String): String = when (value) {
    "AUTO" -> tr("Datumsbeziehung automatisch prüfen", "Determine date relation automatically")
    "ALTERNATIVE" -> tr("Alternative Termine", "Alternative dates")
    "MULTIPLE_OPTIONS" -> tr("Mehrere mögliche Termine", "Multiple possible dates")
    else -> tr("Datumsbeziehung ungeklärt", "Unspecified date relation")
}
