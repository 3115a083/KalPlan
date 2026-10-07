package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.AppointmentCandidate
import cc.stkmn.kalplan.domain.model.CandidateDateRelation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ExtractionDispositionPolicyTest {
    private val policy = ExtractionDispositionPolicy()

    @Test
    fun oneCompleteCandidateIsReady() {
        val result = resultOf(candidate(CandidateDateRelation.SINGLE))

        assertEquals(ExtractionDisposition.READY, policy.evaluate(result).disposition)
    }

    @Test
    fun validAlternativesRequireChoiceButAreNotUnclear() {
        val result = resultOf(
            candidate(CandidateDateRelation.ALTERNATIVE),
            candidate(CandidateDateRelation.ALTERNATIVE)
        )

        assertEquals(
            ExtractionDisposition.USER_CHOICE_REQUIRED,
            policy.evaluate(result).disposition
        )
    }

    @Test
    fun suspiciousYearIssueGoesToUnclear() {
        val base = resultOf(candidate(CandidateDateRelation.SINGLE))
        val result = base.copy(
            issues = listOf(
                ExtractionIssue(
                    code = "explicit_year_suspicious",
                    message = "check",
                    severity = IssueSeverity.NEEDS_REVIEW
                )
            )
        )

        assertEquals(ExtractionDisposition.UNCLEAR, policy.evaluate(result).disposition)
    }

    @Test
    fun unspecifiedMultipleDateRelationIsUnclear() {
        val result = resultOf(
            candidate(CandidateDateRelation.MULTIPLE_UNSPECIFIED),
            candidate(CandidateDateRelation.MULTIPLE_UNSPECIFIED)
        )

        assertEquals(ExtractionDisposition.UNCLEAR, policy.evaluate(result).disposition)
    }

    private fun resultOf(vararg candidates: AppointmentCandidate) = ExtractionResult(
        profileId = null,
        fields = emptyMap(),
        labels = emptyList(),
        candidates = candidates.toList(),
        issues = emptyList()
    )

    private fun candidate(relation: CandidateDateRelation) = AppointmentCandidate(
        start = Instant.parse("2026-12-14T10:00:00Z"),
        end = Instant.parse("2026-12-14T11:00:00Z"),
        durationMinutes = 60,
        locationText = "Dortmund",
        online = false,
        confidence = 0.9,
        localDate = LocalDate.of(2026, 12, 14),
        localStartTime = LocalTime.of(11, 0),
        localEndTime = LocalTime.of(12, 0),
        dateRelation = relation
    )
}
