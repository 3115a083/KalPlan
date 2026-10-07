package cc.stkmn.kalplan.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Feasibility {
    FEASIBLE,
    POSSIBLY_FEASIBLE,
    CONFLICT,
    UNKNOWN
}

enum class RequestStatus {
    UNPROCESSED,
    NEEDS_REVIEW,
    READY,
    REPLY_PREPARED,
    REPLY_SENT,
    RESERVED_PENDING_RESPONSE,
    CONFIRMED,
    DECLINED,
    DISMISSED
}

data class RequestLabel(
    val id: String,
    val name: String,
    val priority: Int = 0,
    val defaultDurationMinutes: Int? = null
)

enum class CandidateDateRelation {
    SINGLE,
    ALTERNATIVE,
    MULTIPLE_OPTIONS,
    MULTIPLE_UNSPECIFIED
}

enum class AppointmentMode {
    ONLINE,
    ONSITE,
    HYBRID,
    UNKNOWN
}

data class AppointmentCandidate(
    val start: Instant?,
    val end: Instant?,
    val durationMinutes: Int?,
    val locationText: String?,
    val online: Boolean?,
    val confidence: Double,
    val warnings: List<String> = emptyList(),
    val localDate: LocalDate? = null,
    val localStartTime: LocalTime? = null,
    val localEndTime: LocalTime? = null,
    val dateRelation: CandidateDateRelation = CandidateDateRelation.SINGLE,
    val mode: AppointmentMode = AppointmentMode.UNKNOWN
)

data class AppointmentRequest(
    val id: String,
    val receivedAt: Instant,
    val sender: String,
    val subject: String,
    val labels: List<RequestLabel> = emptyList(),
    val candidates: List<AppointmentCandidate> = emptyList(),
    val status: RequestStatus = RequestStatus.UNPROCESSED,
    val feasibility: Feasibility = Feasibility.UNKNOWN
)
