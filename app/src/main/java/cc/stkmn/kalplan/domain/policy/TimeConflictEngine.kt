package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.domain.port.CalendarBusyStatus
import cc.stkmn.kalplan.domain.port.CalendarEventRef
import java.time.Duration
import java.time.Instant

enum class TimeConflictStatus {
    CLEAR,
    BUFFER_TIGHT,
    OVERLAP
}

data class TimeConflictResult(
    val status: TimeConflictStatus,
    val conflictingEventIds: List<String>,
    val nearestGapBeforeMinutes: Long?,
    val nearestGapAfterMinutes: Long?
)

class TimeConflictEngine {
    fun evaluate(
        candidateStart: Instant,
        candidateEnd: Instant,
        events: List<CalendarEventRef>,
        bufferBeforeMinutes: Long,
        bufferAfterMinutes: Long
    ): TimeConflictResult {
        require(candidateEnd.isAfter(candidateStart)) { "candidate end must be after start" }

        val blocking = events.filter { it.busyStatus != CalendarBusyStatus.FREE }

        val overlaps = blocking.filter { event ->
            event.start.isBefore(candidateEnd) && event.end.isAfter(candidateStart)
        }

        if (overlaps.isNotEmpty()) {
            return TimeConflictResult(
                status = TimeConflictStatus.OVERLAP,
                conflictingEventIds = overlaps.map { it.id },
                nearestGapBeforeMinutes = null,
                nearestGapAfterMinutes = null
            )
        }

        val previous = blocking
            .filter { !it.end.isAfter(candidateStart) }
            .maxByOrNull { it.end }

        val next = blocking
            .filter { !it.start.isBefore(candidateEnd) }
            .minByOrNull { it.start }

        val beforeGap = previous?.let {
            Duration.between(it.end, candidateStart).toMinutes().coerceAtLeast(0)
        }
        val afterGap = next?.let {
            Duration.between(candidateEnd, it.start).toMinutes().coerceAtLeast(0)
        }

        val tight =
            (beforeGap != null && beforeGap < bufferBeforeMinutes.coerceAtLeast(0)) ||
            (afterGap != null && afterGap < bufferAfterMinutes.coerceAtLeast(0))

        return TimeConflictResult(
            status = if (tight) TimeConflictStatus.BUFFER_TIGHT else TimeConflictStatus.CLEAR,
            conflictingEventIds = emptyList(),
            nearestGapBeforeMinutes = beforeGap,
            nearestGapAfterMinutes = afterGap
        )
    }
}
