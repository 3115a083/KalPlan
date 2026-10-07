package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.AppointmentCandidate
import cc.stkmn.kalplan.domain.model.RequestLabel
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
enum class InputSource {
    COMBINED,
    BODY,
    SUBJECT,
    SENDER
}

@Serializable
enum class ParseDirection {
    TOP_DOWN,
    BOTTOM_UP
}

@Serializable
enum class SemanticField {
    DATE,
    TIME,
    END_TIME,
    DURATION,
    LOCATION,
    ONLINE_OR_LOCATION,
    ONLINE,
    TITLE,
    POSTAL_CODE,
    CITY,
    CUSTOM
}

@Serializable
enum class EvidenceKind {
    PROFILE_RULE,
    STRUCTURED_FIELD,
    HEURISTIC,
    INFERRED_DEFAULT,
    USER_OVERRIDE,
    LOCAL_AI,
    CLOUD_AI
}

data class ExtractionInput(
    val sender: String,
    val subject: String,
    val body: String,
    val receivedAt: Instant,
    val zoneId: ZoneId = ZoneId.of("Europe/Berlin")
) {
    val combined: String
        get() = buildString {
            if (subject.isNotBlank()) append(subject.trim())
            if (subject.isNotBlank() && body.isNotBlank()) append("\n")
            append(body)
        }
}

data class ExtractionEvidence(
    val kind: EvidenceKind,
    val source: InputSource,
    val ruleId: String?,
    val matchedText: String,
    val confidence: Double,
    val startIndex: Int? = null,
    val endIndexExclusive: Int? = null
)

data class ExtractedValue(
    val key: String,
    val semantic: SemanticField,
    val value: String,
    val evidence: ExtractionEvidence
)

@Serializable
enum class IssueSeverity {
    INFO,
    WARNING,
    NEEDS_REVIEW
}

data class ExtractionIssue(
    val code: String,
    val message: String,
    val severity: IssueSeverity,
    val fieldKey: String? = null
)

data class ExtractionResult(
    val profileId: String?,
    val fields: Map<String, ExtractedValue>,
    val labels: List<RequestLabel>,
    val candidates: List<AppointmentCandidate>,
    val issues: List<ExtractionIssue>
)
