package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.CandidateDateRelation

enum class ExtractionDisposition {
    READY,
    USER_CHOICE_REQUIRED,
    UNCLEAR
}

data class ExtractionDispositionResult(
    val disposition: ExtractionDisposition,
    val reasons: List<String>
)

class ExtractionDispositionPolicy(
    private val minimumReadyConfidence: Double = 0.70
) {
    fun evaluate(result: ExtractionResult): ExtractionDispositionResult {
        val reasons = mutableListOf<String>()

        if (result.candidates.isEmpty()) {
            return ExtractionDispositionResult(
                ExtractionDisposition.UNCLEAR,
                listOf("no_candidate")
            )
        }

        if (result.issues.any { it.severity == IssueSeverity.NEEDS_REVIEW }) {
            reasons += result.issues
                .filter { it.severity == IssueSeverity.NEEDS_REVIEW }
                .map { it.code }
        }

        if (result.candidates.any { it.start == null }) {
            reasons += "candidate_missing_start"
        }

        if (result.candidates.any { it.confidence < minimumReadyConfidence }) {
            reasons += "low_confidence"
        }

        if (result.candidates.any {
                it.dateRelation == CandidateDateRelation.MULTIPLE_UNSPECIFIED
            }
        ) {
            reasons += "multiple_dates_relation_unclear"
        }

        if (reasons.isNotEmpty()) {
            return ExtractionDispositionResult(
                ExtractionDisposition.UNCLEAR,
                reasons.distinct()
            )
        }

        if (result.candidates.size > 1 || result.candidates.any {
                it.dateRelation == CandidateDateRelation.ALTERNATIVE ||
                    it.dateRelation == CandidateDateRelation.MULTIPLE_OPTIONS
            }
        ) {
            return ExtractionDispositionResult(
                ExtractionDisposition.USER_CHOICE_REQUIRED,
                listOf("multiple_valid_candidates")
            )
        }

        return ExtractionDispositionResult(
            ExtractionDisposition.READY,
            emptyList()
        )
    }
}
