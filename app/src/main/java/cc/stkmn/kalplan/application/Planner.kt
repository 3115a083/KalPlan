package cc.stkmn.kalplan.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.infrastructure.calendar.AndroidCalendarReader
import java.time.*

/** Android I/O boundary; feasibility decisions remain in AppointmentAssessment. */
class Planner(private val context: Context, private val repository: AppRepository) {
    suspend fun assess(request: StoredRequest): Assessment {
        val data = repository.data.value
        val settings = data.settings
        val candidate = request.candidate
            ?: return AppointmentAssessment.evaluate(request, data, emptyList())
        val window = AppointmentAssessment.queryWindow(candidate, settings, ZoneId.systemDefault())
            ?: return AppointmentAssessment.evaluate(request, data, emptyList())
        val selected = data.calendars.filter { it.included }
        if (selected.isEmpty()) return AppointmentAssessment.evaluate(request, data, emptyList())
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return Assessment("UNKNOWN", listOf("calendar_permission"), emptyList(), settings.originAddress)
        val events = AndroidCalendarReader(context).events(window.from, window.to, selected.map { it.id }.toSet())
        return AppointmentAssessment.evaluate(request, data, events)
    }
}
