package cc.stkmn.kalplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import cc.stkmn.kalplan.data.StoredCandidate
import cc.stkmn.kalplan.data.StoredRequest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun tr(de: String, en: String): String = if (LocalConfiguration.current.locales[0].language == "de") de else en
fun appointmentTime(candidate: StoredCandidate?): String {
    val start = candidate?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) } ?: return "?"
    val end = candidate.endMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    return start.format(DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy · HH:mm", Locale.getDefault())) + (end?.let { " – " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "")
}
@Composable fun statusText(status: String): String = when(status) {
    "FEASIBLE" -> tr("Machbar", "Feasible")
    "POSSIBLE" -> tr("Möglicherweise machbar", "Possibly feasible")
    "CONFLICT" -> tr("Konflikt", "Conflict")
    "NEW" -> tr("Offen", "New")
    "LATER" -> tr("Später", "Later")
    "DECLINED" -> tr("Abgelehnt", "Declined")
    "WAITING" -> tr("Wartet auf Rückmeldung", "Waiting for response")
    "RESERVED" -> tr("Reserviert", "Reserved")
    "RESERVATION_FAILED" -> tr("Antwort gesendet. Reservierung fehlgeschlagen", "Reply sent. Reservation failed")
    "DELIVERY_UNKNOWN" -> tr("Versand ungewiss. Postausgang prüfen", "Delivery uncertain. Check sent mail")
    "TEST_SENT" -> tr("Testantwort gesendet", "Test reply sent")
    "SENDING" -> tr("Wird gesendet", "Sending")
    "DISMISSED" -> tr("Lokal entfernt", "Locally dismissed")
    else -> tr("Unklar", "Unclear")
}
@Composable fun reasonText(code: String): String = when(code) {
    "time_overlap" -> tr("Überschneidung mit einem belegten Termin", "Overlaps a busy event")
    "buffer_tight" -> tr("Der eingestellte Puffer reicht nicht", "Configured buffer is too short")
    "time_clear" -> tr("Keine Zeitüberschneidung", "No time overlap")
    "select_calendars" -> tr("Kalender in den Einstellungen auswählen", "Select calendars in settings")
    "calendar_permission" -> tr("Kalenderberechtigung fehlt", "Calendar permission missing")
    "select_candidate" -> tr("Terminkandidat auswählen", "Select an appointment candidate")
    "travel_unchecked" -> tr("Fahrt zum Termin noch ungeprüft", "Travel to appointment is unchecked")
    "travel_after_unchecked" -> tr("Fahrt zum Folgetermin noch ungeprüft", "Travel to the following event is unchecked")
    "travel_before" -> tr("Fahrt vor dem Termin passt nicht in die Lücke", "Travel before appointment exceeds the gap")
    "travel_after" -> tr("Fahrt zum Folgetermin passt nicht in die Lücke", "Travel to the following event exceeds the gap")
    "dst_time_ambiguous_or_invalid" -> tr("Zeitumstellung: UTC-Offset manuell bestätigen", "Daylight saving transition: confirm UTC offset manually")
    "mime_limits_exceeded" -> tr("Mail überschreitet die sicheren Größen- oder MIME-Grenzen. Original im Mailprogramm prüfen.", "Email exceeds safe size or MIME limits. Check the original in your mail app.")
    "route_stale" -> tr("Fahrzeitschätzung ist älter als eine Stunde", "Travel estimate is over an hour old")
    "location_unclear" -> tr("Ort oder Durchführung unklar", "Location or meeting mode is unclear")
    else -> code
}
