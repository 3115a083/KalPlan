package cc.stkmn.kalplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.data.AppRepository
import cc.stkmn.kalplan.infrastructure.address.AddressSearch
import kotlinx.coroutines.delay

@Composable
fun AddressField(label: String, value: String, onValue: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppRepository.get(context.applicationContext) }
    val state by repository.data.collectAsState()
    var focused by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var feedback by remember { mutableStateOf("") }
    val language = tr("de", "en")
    LaunchedEffect(value, focused, edited, state.settings.addressSuggestionsEnabled) {
        suggestions = emptyList(); feedback = ""
        if (!focused || !edited || value.trim().length < 3 || !state.settings.addressSuggestionsEnabled) return@LaunchedEffect
        delay(650)
        try { suggestions = AddressSearch.suggest(repository, value, language) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { feedback = if (language == "de") "Keine Vorschläge. Adresse kann frei eingegeben werden; Tageslimit oder Verbindung prüfen." else "No suggestions. Type an address freely; check daily limit or connection." }
    }
    Column {
        StableTextField(value, { edited = true; onValue(it.take(1000)) }, label = { Text(label) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.LocationOn, null) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (!focused) suggestions = emptyList() })
        suggestions.forEach { address -> ListItem(headlineContent = { Text(address) }, modifier = Modifier.clickable {
            edited = false; suggestions = emptyList(); onValue(address)
        }) }
        if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AddressSetting(label: String, value: String, onValue: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(label) }, supportingContent = { Text(value) }, leadingContent = { Icon(Icons.Outlined.LocationOn, null) })
    }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(value) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = { Column { AddressField(label, draft) { draft = it }; Text(tr("Online-Vorschläge lassen sich unter Planung aktivieren. Dabei geht der eingegebene Suchtext an Photon.", "Enable online suggestions under Planning. The typed search text is sent to Photon."), style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { onValue(draft); open = false }) { Text(tr("Übernehmen", "Apply")) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Abbrechen", "Cancel")) } })
    }
}
