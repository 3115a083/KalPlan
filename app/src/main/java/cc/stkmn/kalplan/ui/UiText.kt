package cc.stkmn.kalplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import cc.stkmn.kalplan.data.StoredCandidate
import cc.stkmn.kalplan.data.StoredRequest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { "SYSTEM" }
@Composable fun tr(de: String, en: String): String {
    val configured = LocalAppLanguage.current
    val german = configured == "DE" || configured == "SYSTEM" && LocalConfiguration.current.locales[0].language == "de"
    return if (german) de else en
}
@Composable fun appointmentTime(candidate: StoredCandidate?): String {
    val start = candidate?.startMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) } ?: return "?"
    val end = candidate.endMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val configured = LocalAppLanguage.current
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = when (configured) { "DE" -> Locale.GERMANY; "EN" -> Locale.US; else -> systemLocale }
    val pattern = if (locale.language == "de") "EEE, dd.MM.yyyy · HH:mm" else "EEE, MM/dd/yyyy · HH:mm"
    return start.format(DateTimeFormatter.ofPattern(pattern, locale)) + (end?.let { " – " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "")
}
fun localDateText(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "dd.MM.yyyy" else "MM/dd/yyyy", locale))
fun parseLocalDateText(value: String, locale: Locale = Locale.getDefault()): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrElse {
        LocalDate.parse(value, DateTimeFormatter.ofPattern(if (locale.language == "de") "dd.MM.yyyy" else "MM/dd/yyyy", locale))
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
    "heuristic_date" -> tr("Datum aus Freitext geschätzt", "Date estimated from free text")
    "heuristic_time" -> tr("Uhrzeit aus Freitext geschätzt", "Time estimated from free text")
    "heuristic_location" -> tr("Ort aus Freitext geschätzt", "Location estimated from free text")
    "year_inferred" -> tr("Jahr aus dem Empfangsdatum ergänzt", "Year inferred from received date")
    "relative_explicit_date_conflict" -> tr("Relative und konkrete Datumsangabe widersprechen sich", "Relative and explicit dates conflict")
    "fallback_context_conflict" -> tr("Geschätzte Angaben gehören möglicherweise zu einem anderen Termin", "Estimated values may belong to another appointment")
    "ambiguous_heuristic_context", "ambiguous_heuristic_time" -> tr("Mehrere mögliche Terminangaben. Zuordnung prüfen.", "Multiple possible appointment values. Review their assignment.")
    "ambiguous_heuristic_location", "uncertain_heuristic_location" -> tr("Geschätzten Ort prüfen", "Review estimated location")
    "date_missing", "configured_field_unavailable" -> tr("Benötigte Terminangabe fehlt oder ist leer", "Required appointment value is missing or empty")
    "time_missing" -> tr("Beginn konnte nicht erkannt werden", "Start time could not be extracted")
    "required_field_missing", "required_field_empty", "invalid_field_type" -> tr("Pflichtfeld oder Datentyp prüfen", "Review required field or data type")
    "conflicting_field_matches", "conflicting_semantic_fields" -> tr("Widersprüchliche Feldtreffer. Profil verfeinern.", "Conflicting field matches. Refine the profile.")
    "unmapped_label_value", "label_target_missing" -> tr("Labelzuordnung prüfen", "Review label mapping")
    "unmapped_meeting_mode" -> tr("Zuordnung der Terminart prüfen", "Review meeting-mode mapping")
    "multiple_matching_profiles" -> tr("Mehrere Profile passen. Profil auswählen.", "Multiple profiles match. Choose a profile.")
    "time_overlap" -> tr("Überschneidung mit einem belegten Termin", "Overlaps a busy event")
    "buffer_tight" -> tr("Der eingestellte Puffer reicht nicht", "Configured buffer is too short")
    "time_clear" -> tr("Keine Zeitüberschneidung", "No time overlap")
    "invalid_interval" -> tr("Ende muss nach dem Beginn liegen. Angaben korrigieren.", "End must be after start. Correct the appointment details.")
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
