package cc.stkmn.kalplan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.data.AppRepository
import cc.stkmn.kalplan.infrastructure.backup.*
import cc.stkmn.kalplan.infrastructure.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BackupSettings(repository: AppRepository, busy: Boolean, onRun: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var includeSecrets by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var snapshot by remember { mutableStateOf<BackupSnapshot?>(null) }
    var feedback by remember { mutableStateOf("") }
    var confirmUndo by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val content = bytes; bytes = null
        if (uri != null && content != null) onRun {
            try {
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(content) } }
                feedback = "Sicherung gespeichert / Backup saved"
            } finally { content.fill(0) }
        } else content?.fill(0)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRun {
            bytes = withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= PortableBackup.MAX_BYTES); output.write(buffer, 0, count) }
                output.toByteArray()
            } }
            importing = true; password = ""
        }
    }
    Text(tr("Passwortgeschützte Sicherung von Einstellungen, Labels, Profilen, Konten und Anfragen. Das Passwort wird auf dem neuen Gerät benötigt.", "Password-protected backup of settings, labels, profiles, accounts and requests. The password is required on the new device."))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { password = ""; exporting = true }) { Text(tr("Exportieren", "Export")) }
        OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("application/octet-stream", "*/*")) }) { Text(tr("Importieren", "Import")) }
    }
    TextButton(enabled = !busy, onClick = { confirmUndo = true }) { Text(tr("Letzten Import rückgängig machen", "Undo last import")) }
    if (feedback.isNotBlank()) Text(feedback)
    if (exporting || importing) AlertDialog(onDismissRequest = { if (!busy) { exporting = false; importing = false; password = ""; bytes?.fill(0); bytes = null } },
        title = { Text(if (exporting) tr("Sicherung exportieren", "Export backup") else tr("Sicherung öffnen", "Open backup")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecretField(tr("Sicherungspasswort, mindestens 10 Zeichen", "Backup password, at least 10 characters"), password) { password = it }
            if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.error)
            if (exporting) ToggleRow(tr("Zugangsdaten und Tokens einschließen", "Include credentials and tokens"), includeSecrets) { includeSecrets = it }
            Text(tr("Die Datei enthält persönliche Daten und bleibt verschlüsselt. Signaturdateien müssen auf einem neuen Gerät erneut ausgewählt werden.", "The file contains personal data and stays encrypted. Signature files must be selected again on a new device."), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(enabled = !busy && password.length >= 10, onClick = {
            val secret = password.toCharArray(); password = ""
            if (exporting) onRun {
                try {
                    val source = repository.backup(includeSecrets)
                    bytes = withContext(Dispatchers.Default) { PortableBackup.encode(source, secret) }
                    exporting = false
                    save.launch("KalPlan-${java.time.LocalDate.now()}.kalplan")
                } finally { secret.fill('\u0000') }
            } else onRun {
                try {
                    snapshot = withContext(Dispatchers.Default) { PortableBackup.decode(requireNotNull(bytes), secret) }
                    importing = false; bytes?.fill(0); bytes = null
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { feedback = "Passwort falsch oder Datei beschädigt / Wrong password or damaged backup" }
                finally { secret.fill('\u0000') }
            }
        }) { Text(if (exporting) tr("Datei speichern", "Save file") else tr("Prüfen", "Verify")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { exporting = false; importing = false; password = ""; bytes?.fill(0); bytes = null }) { Text(tr("Abbrechen", "Cancel")) } })
    snapshot?.let { source -> AlertDialog(onDismissRequest = { if (!busy) snapshot = null }, title = { Text(tr("Daten ersetzen?", "Replace data?")) },
        text = { Text("${source.data.accounts.size} " + tr("Konten", "accounts") + ", ${source.data.profiles.size} " + tr("Profile", "profiles") + ", ${source.data.requests.size} " + tr("Anfragen", "requests") + "\n\n" + tr("Der aktuelle Stand wird vorab lokal gesichert und ersetzt. Konten bleiben zunächst deaktiviert, Sync manuell. Kalender und Signaturdateien müssen erneut zugeordnet werden. Import sendet keine Mails und schreibt keine Kalendertermine.", "The current state is saved locally before replacement. Accounts start disabled, sync is manual. Select calendars and signature files again. Import sends no mail and writes no calendar events.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { onRun { repository.restore(source); SyncScheduler.configure(context, 0); snapshot = null; feedback = "Import abgeschlossen. Konten und Kalender prüfen / Import complete. Review accounts and calendars." } }) { Text(tr("Jetzt ersetzen", "Replace now")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { snapshot = null }) { Text(tr("Abbrechen", "Cancel")) } }) }
    if (confirmUndo) AlertDialog(onDismissRequest = { confirmUndo = false }, title = { Text(tr("Vorherigen Stand wiederherstellen?", "Restore previous state?")) }, text = { Text(tr("Der Stand vor dem letzten Import wird wiederhergestellt. Verbindungen bleiben zur Prüfung pausiert.", "Restore the state before the last import. Connections remain paused for review.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { onRun { repository.restorePrevious(); SyncScheduler.configure(context, 0); confirmUndo = false; feedback = "Vorheriger Stand wiederhergestellt / Previous state restored" } }) { Text(tr("Wiederherstellen", "Restore")) } }, dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text(tr("Abbrechen", "Cancel")) } })
}
