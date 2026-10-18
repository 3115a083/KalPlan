package cc.stkmn.kalplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.domain.port.CalendarEventRef
import cc.stkmn.kalplan.domain.policy.CalendarDates
import java.time.*
import java.time.format.TextStyle

@Composable
fun CalendarDayDots(day: LocalDate, events: List<CalendarEventRef>, colors: Map<String, Color>) {
    val ids = events.filter { CalendarDates.overlapsDay(it.start, it.end, day, ZoneId.systemDefault()) }.map { it.calendarId }.distinct()
    Row(Modifier.height(10.dp).semantics { contentDescription = "${ids.size} calendars with appointments" }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ids.take(4).forEach { id -> Box(Modifier.size(6.dp).background(colors[id] ?: MaterialTheme.colorScheme.secondary, CircleShape)) }
        if (ids.size > 4) Text("+", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MonthGrid(month: YearMonth, selected: LocalDate, events: List<CalendarEventRef>, colors: Map<String, Color>, onSelect: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth()) { (1..7).forEach { d -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall) } } }
    CalendarDates.monthDays(month).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Surface(modifier = Modifier.weight(1f).heightIn(min = 52.dp).clickable { onSelect(date) },
                    shape = MaterialTheme.shapes.small,
                    color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (date.month == month.month) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(date.dayOfMonth.toString())
                        CalendarDayDots(date, events, colors)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthJumpDialog(selected: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    var year by rememberSaveable { mutableStateOf(selected.year.toString()) }
    val valid = year.toIntOrNull()?.takeIf { it in 1900..2200 }
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Monat und Jahr", "Month and year")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year = ((valid ?: selected.year) - 1).coerceAtLeast(1900).toString() }) { Text("‹") }
                StableTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Jahr", "Year")) }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { year = ((valid ?: selected.year) + 1).coerceAtMost(2200).toString() }) { Text("›") }
            }
            (1..12).chunked(3).forEach { months -> Row(Modifier.fillMaxWidth()) {
                months.forEach { m -> TextButton(enabled = valid != null, modifier = Modifier.weight(1f), onClick = { onSelect(CalendarDates.moveMonth(selected, requireNotNull(valid), m)) }) { Text(Month.of(m).getDisplayName(TextStyle.SHORT, locale)) } }
            } }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen", "Close")) } })
}
