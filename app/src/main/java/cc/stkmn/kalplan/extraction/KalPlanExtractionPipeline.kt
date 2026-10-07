package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.AppointmentCandidate
import cc.stkmn.kalplan.domain.model.AppointmentMode
import cc.stkmn.kalplan.domain.model.CandidateDateRelation
import java.time.ZonedDateTime

class KalPlanExtractionPipeline(
    private val profileEngine: ProfileExtractionEngine = ProfileExtractionEngine(),
    private val temporalParser: FlexibleTemporalParser = FlexibleTemporalParser(),
    private val onlineClassifier: OnlineClassifier = OnlineClassifier(),
    private val labelRuleEngine: LabelRuleEngine = LabelRuleEngine(),
    private val textNormalizer: MailTextNormalizer = MailTextNormalizer(),
    private val locationHeuristicExtractor: LocationHeuristicExtractor = LocationHeuristicExtractor()
) {
    fun extract(
        input: ExtractionInput,
        profile: ExtractionProfile?,
        labelRules: List<LabelRule> = emptyList()
    ): ExtractionResult {
        val normalizedInput = textNormalizer.normalize(input)

        val profileResult = if (profile != null) {
            profileEngine.extract(normalizedInput, profile)
        } else {
            ProfileExtractionEngine.ProfileExtraction(emptyMap(), emptyList())
        }

        val fields = profileResult.values
        val issues = profileResult.issues.toMutableList()
        val labels = labelRuleEngine.labels(normalizedInput, labelRules)

        val dateField = firstSemantic(fields, SemanticField.DATE)
        val timeField = firstSemantic(fields, SemanticField.TIME)
        val endTimeField = firstSemantic(fields, SemanticField.END_TIME)
        val durationField = firstSemantic(fields, SemanticField.DURATION)
        val locationField = firstSemantic(fields, SemanticField.LOCATION)
        val onlineField = firstSemantic(fields, SemanticField.ONLINE)
            ?: firstSemantic(fields, SemanticField.ONLINE_OR_LOCATION)
        val combinedOnlineLocation = firstSemantic(fields, SemanticField.ONLINE_OR_LOCATION)

        val dateText = dateField?.value ?: normalizedInput.combined
        val timeText = when {
            timeField != null && endTimeField != null ->
                timeField.value + " - " + endTimeField.value
            timeField != null ->
                timeField.value
            else ->
                normalizedInput.combined
        }

        val temporal = temporalParser.parse(
            dateText = dateText,
            timeText = timeText,
            durationText = durationField?.value ?: normalizedInput.combined,
            locale = profile?.locale ?: TemporalLocale.DE_DE,
            reference = ZonedDateTime.ofInstant(normalizedInput.receivedAt, normalizedInput.zoneId)
        )
        issues += temporal.issues

        val modeResult = onlineClassifier.classify(
            structuredValue = onlineField?.value,
            body = normalizedInput.combined
        )

        val heuristicLocation = if (locationField == null && combinedOnlineLocation == null) {
            locationHeuristicExtractor.extract(normalizedInput.combined)
        } else {
            null
        }

        val location = when {
            locationField != null -> locationField.value
            combinedOnlineLocation != null &&
                modeResult.mode != MeetingMode.ONLINE &&
                modeResult.mode != MeetingMode.HYBRID ->
                combinedOnlineLocation.value
            heuristicLocation != null -> heuristicLocation.value
            else -> null
        }

        if (temporal.candidates.size > 1) {
            issues += ExtractionIssue(
                code = when (temporal.candidates.first().relation) {
                    DateRelation.ALTERNATIVE -> "alternative_dates"
                    DateRelation.MULTIPLE_OPTIONS -> "multiple_date_options"
                    DateRelation.MULTIPLE_UNSPECIFIED -> "multiple_dates_unclear"
                    DateRelation.SINGLE -> "multiple_dates"
                },
                message = "Multiple date candidates were extracted and must stay separate.",
                severity = if (temporal.candidates.first().relation == DateRelation.ALTERNATIVE) {
                    IssueSeverity.INFO
                } else {
                    IssueSeverity.WARNING
                }
            )
        }

        val candidates = temporal.candidates.map { candidate ->
            val structuredConfidence = listOfNotNull(
                dateField?.evidence?.confidence,
                timeField?.evidence?.confidence
            ).minOrNull()

            val timeConfidence = structuredConfidence ?: if (profile == null) 0.72 else 0.82
            val confidence = minOf(candidate.confidence, timeConfidence)

            AppointmentCandidate(
                start = candidate.start?.toInstant(),
                end = candidate.end?.toInstant(),
                durationMinutes = temporal.durationMinutes,
                locationText = location,
                online = when (modeResult.mode) {
                    MeetingMode.ONLINE -> true
                    MeetingMode.ONSITE -> false
                    MeetingMode.HYBRID -> true
                    MeetingMode.UNKNOWN -> null
                },
                confidence = confidence,
                warnings = candidate.warnings,
                localDate = candidate.date,
                localStartTime = candidate.startTime,
                localEndTime = candidate.endTime,
                dateRelation = candidate.relation.toDomain(),
                mode = modeResult.mode.toDomain()
            )
        }

        if (candidates.isEmpty()) {
            issues += ExtractionIssue(
                code = "no_appointment_candidate",
                message = "No usable appointment candidate could be created.",
                severity = IssueSeverity.NEEDS_REVIEW
            )
        }

        return ExtractionResult(
            profileId = profile?.id,
            fields = fields,
            labels = labels,
            candidates = candidates,
            issues = issues.distinctBy { Triple(it.code, it.fieldKey, it.message) }
        )
    }

    private fun firstSemantic(
        fields: Map<String, ExtractedValue>,
        semantic: SemanticField
    ): ExtractedValue? = fields.values.firstOrNull { it.semantic == semantic }

    private fun DateRelation.toDomain(): CandidateDateRelation = when (this) {
        DateRelation.SINGLE -> CandidateDateRelation.SINGLE
        DateRelation.ALTERNATIVE -> CandidateDateRelation.ALTERNATIVE
        DateRelation.MULTIPLE_OPTIONS -> CandidateDateRelation.MULTIPLE_OPTIONS
        DateRelation.MULTIPLE_UNSPECIFIED -> CandidateDateRelation.MULTIPLE_UNSPECIFIED
    }

    private fun MeetingMode.toDomain(): AppointmentMode = when (this) {
        MeetingMode.ONLINE -> AppointmentMode.ONLINE
        MeetingMode.ONSITE -> AppointmentMode.ONSITE
        MeetingMode.HYBRID -> AppointmentMode.HYBRID
        MeetingMode.UNKNOWN -> AppointmentMode.UNKNOWN
    }
}
