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
        val normalizedInput = textNormalizer.normalize(input.copy(zoneId = profile?.extractionZoneId?.let {
            runCatching { java.time.ZoneId.of(it) }.getOrDefault(input.zoneId)
        } ?: input.zoneId))

        val profileResult = if (profile != null) {
            profileEngine.extract(normalizedInput, profile)
        } else {
            ProfileExtractionEngine.ProfileExtraction(emptyMap(), emptyList())
        }

        val fields = profileResult.values.toMutableMap()
        val issues = profileResult.issues.toMutableList()
        val labels = labelRuleEngine.labels(normalizedInput, labelRules).toMutableList()
        val semanticFields = linkedMapOf<String, ExtractedValue>()
        fields.values.forEach { field ->
            semanticFields[field.key] = field
            profile?.extractors?.firstOrNull { it.key == field.key }?.additionalSemantics.orEmpty().forEach { semantic ->
                if (semantic != field.semantic) semanticFields[field.key + ":" + semantic.name] = field.copy(semantic = semantic)
            }
        }
        profile?.extractors?.forEach { rule ->
            val raw = fields[rule.key]?.value ?: return@forEach
            if (raw.isNotBlank() && rule.labelMappings.isNotEmpty()) {
                val mapped = rule.labelMappings.filter { it.value.trim().equals(raw.trim(), ignoreCase = true) }
                if (mapped.isEmpty()) issues += ExtractionIssue("unmapped_label_value", "Label-Zuordnung für '" + rule.displayName.ifBlank { rule.key } + "' prüfen.", IssueSeverity.NEEDS_REVIEW, rule.key)
                mapped.forEach { labels += cc.stkmn.kalplan.domain.model.RequestLabel(it.label, it.label) }
            }
        }

        val dateFields = semanticValues(semanticFields, SemanticField.DATE)
        val timeField = firstSemantic(semanticFields, SemanticField.TIME)
        val endTimeField = firstSemantic(semanticFields, SemanticField.END_TIME)
        val durationField = firstSemantic(semanticFields, SemanticField.DURATION)
        val locationField = firstSemantic(semanticFields, SemanticField.LOCATION)
        val onlineField = firstSemantic(semanticFields, SemanticField.ONLINE)
            ?: firstSemantic(semanticFields, SemanticField.ONLINE_OR_LOCATION)
        val combinedOnlineLocation = firstSemantic(semanticFields, SemanticField.ONLINE_OR_LOCATION)
        fun configured(semantic: SemanticField) = profile?.extractors.orEmpty().any {
            (it.semantic == semantic || semantic in it.additionalSemantics) &&
                (!it.allowHeuristicFallback || fields[it.key]?.value?.isBlank() == true ||
                    it.sourceVariableKey != null && fields[it.key] == null)
        }
        val heuristicInput = normalizedInput.copy(body = normalizedInput.body.let { original ->
            // A deliberately separated informational region must not become a missing appointment field.
            profile?.extractors.orEmpty().filter {
                it.sourceVariableKey == null && (
                    it.semantic == SemanticField.CUSTOM && it.additionalSemantics.isEmpty() &&
                        profile?.extractors.orEmpty().none { dependent -> dependent.sourceVariableKey == it.key } ||
                    timeField == null && (it.semantic == SemanticField.END_TIME || it.semantic == SemanticField.DURATION))
            }
                .mapNotNull { fields[it.key]?.evidence?.takeIf { e -> e.source == InputSource.BODY && e.startIndex != null && e.endIndexExclusive != null } }
                .sortedByDescending { it.startIndex }.fold(original) { text, evidence ->
                    val a = requireNotNull(evidence.startIndex).coerceIn(0, text.length)
                    val b = requireNotNull(evidence.endIndexExclusive).coerceIn(a, text.length)
                    text.replaceRange(a, b, " ".repeat(b - a))
                }
        })
        val fallbackSources = LocalHeuristicExtractor.temporalSources(heuristicInput, profile?.locale ?: TemporalLocale.DE_DE)
        fun fallback(semantic: SemanticField) = if (configured(semantic)) "" else when (semantic) {
            SemanticField.DATE -> fallbackSources.dateText
            SemanticField.TIME, SemanticField.END_TIME -> fallbackSources.timeText
            else -> LocalHeuristicExtractor.context(heuristicInput)
        }
        var usesDateFallback = dateFields.isEmpty() && !configured(SemanticField.DATE)
        var usesTimeFallback = timeField == null && !configured(SemanticField.TIME)
        if ((usesDateFallback || usesTimeFallback) && fallbackSources.ambiguous)
            issues += ExtractionIssue("ambiguous_heuristic_context", "Mehrere mögliche Terminangaben im Mailtext. Zuordnung prüfen.", IssueSeverity.NEEDS_REVIEW)
        for (semantic in listOf(SemanticField.DATE, SemanticField.TIME, SemanticField.END_TIME, SemanticField.LOCATION, SemanticField.ONLINE)) {
            val extracted = semanticValues(semanticFields, semantic)
            if (configured(semantic) && extracted.none { it.value.isNotBlank() } && semantic in setOf(SemanticField.DATE, SemanticField.TIME))
                issues += ExtractionIssue("configured_field_unavailable", "Konfigurierte Terminangabe fehlt oder ist leer: " + semantic.name, IssueSeverity.NEEDS_REVIEW)
            if (extracted.map { it.value.trim() }.filter { it.isNotEmpty() }.distinct().size > 1 && semantic != SemanticField.DATE)
                issues += ExtractionIssue("conflicting_semantic_fields", "Mehrere Felder liefern unterschiedliche Werte: " + semantic.name, IssueSeverity.NEEDS_REVIEW)
        }

        var dateText = structuredDateText(
            dateFields = dateFields,
            mode = profile?.multipleDateMode ?: MultipleDateMode.AUTO
        ) ?: fallback(SemanticField.DATE)
        var timeText = when {
            timeField?.value?.isNotBlank() == true && endTimeField?.value?.isNotBlank() == true ->
                timeField.value + " - " + endTimeField.value
            timeField != null ->
                timeField.value
            else ->
                fallback(SemanticField.TIME)
        }

        if (usesTimeFallback && endTimeField?.value?.isNotBlank() == true) {
            val guess = temporalParser.parse(dateText, timeText, locale = profile?.locale ?: TemporalLocale.DE_DE,
                reference = ZonedDateTime.ofInstant(normalizedInput.receivedAt, normalizedInput.zoneId)).candidates.firstOrNull()?.startTime
            timeText = guess?.let { "$it - ${endTimeField.value}" }.orEmpty()
        }

        // Fallback evidence from another appointment must never complete a structured record.
        val reference = ZonedDateTime.ofInstant(normalizedInput.receivedAt, normalizedInput.zoneId)
        if (usesDateFallback || usesTimeFallback) {
            val guessed = temporalParser.parse(fallbackSources.dateText, fallbackSources.timeText, locale = profile?.locale ?: TemporalLocale.DE_DE, reference = reference)
            if (usesTimeFallback && dateFields.any { it.value.isNotBlank() }) {
                val knownDates = temporalParser.parse(dateText, "", locale = profile?.locale ?: TemporalLocale.DE_DE, reference = reference).candidates.map { it.date }.toSet()
                if (knownDates.isNotEmpty() && guessed.candidates.any { it.date !in knownDates }) {
                    timeText = ""
                    issues += ExtractionIssue("fallback_context_conflict", "Uhrzeitvorschlag gehört zu einem anderen Datum. Bitte zuordnen.", IssueSeverity.NEEDS_REVIEW)
                }
            }
            if (usesDateFallback && timeField?.value?.isNotBlank() == true) {
                val knownTime = temporalParser.parse(reference.toLocalDate().toString(), timeText, locale = profile?.locale ?: TemporalLocale.DE_DE, reference = reference).candidates.firstOrNull()?.startTime
                if (knownTime != null && guessed.candidates.any { it.startTime != null && it.startTime != knownTime }) {
                    dateText = ""
                    issues += ExtractionIssue("fallback_context_conflict", "Datumsvorschlag gehört zu einer anderen Uhrzeit. Bitte zuordnen.", IssueSeverity.NEEDS_REVIEW)
                }
            }
        }

        // Free text without a duration is normal. Keep malformed configured values
        // for validation, but do not present an absent optional guess as a parse error.
        val durationText = durationField?.value ?: fallback(SemanticField.DURATION)
            .takeIf { temporalParser.parseDurationMinutes(it) != null }.orEmpty()
        var temporal = temporalParser.parse(
            dateText = dateText,
            timeText = timeText,
            durationText = durationText,
            locale = profile?.locale ?: TemporalLocale.DE_DE,
            reference = ZonedDateTime.ofInstant(normalizedInput.receivedAt, normalizedInput.zoneId)
        )
        if (temporal.candidates.isEmpty() && !usesDateFallback && dateFields.any { it.value.isNotBlank() } && !configured(SemanticField.DATE)) {
            usesDateFallback = true
            dateText = fallbackSources.dateText
            issues += ExtractionIssue("profile_date_unparsed", "Profilwert ist kein nutzbares Datum. Freitextvorschlag prüfen.", IssueSeverity.NEEDS_REVIEW)
            temporal = temporalParser.parse(dateText, timeText, durationField?.value ?: "", profile?.locale ?: TemporalLocale.DE_DE,
                ZonedDateTime.ofInstant(normalizedInput.receivedAt, normalizedInput.zoneId))
        }
        if (temporal.candidates.none { it.startTime != null } && !usesTimeFallback && timeField?.value?.isNotBlank() == true && !configured(SemanticField.TIME)) {
            usesTimeFallback = true
            issues += ExtractionIssue("profile_time_unparsed", "Profilwert ist keine nutzbare Uhrzeit. Freitextvorschlag prüfen.", IssueSeverity.NEEDS_REVIEW)
            if (fallbackSources.ambiguous) issues += ExtractionIssue("ambiguous_heuristic_time", "Mehrere mögliche Zeitangaben im Mailtext.", IssueSeverity.NEEDS_REVIEW)
            temporal = temporalParser.parse(dateText, fallbackSources.timeText, durationField?.value ?: "", profile?.locale ?: TemporalLocale.DE_DE,
                ZonedDateTime.ofInstant(normalizedInput.receivedAt, normalizedInput.zoneId))
        }
        issues += temporal.issues

        val onlineRule = profile?.extractors?.firstOrNull { it.key == onlineField?.key }
        val mappedMode = onlineRule?.modeMappings?.entries?.firstOrNull { it.key.trim().equals(onlineField?.value?.trim(), true) }?.value
        val modeResult = if (onlineRule?.modeMappings?.isNotEmpty() == true) {
            val mode = mappedMode?.let { runCatching { MeetingMode.valueOf(it) }.getOrNull() } ?: MeetingMode.UNKNOWN
            if (onlineField?.value?.isNotBlank() == true && mode == MeetingMode.UNKNOWN)
                issues += ExtractionIssue("unmapped_meeting_mode", "Terminart-Zuordnung prüfen.", IssueSeverity.NEEDS_REVIEW, onlineField.key)
            MeetingModeResult(mode, if (mode == MeetingMode.UNKNOWN) 0.0 else 0.99, onlineField?.value)
        } else onlineClassifier.classify(
            structuredValue = onlineField?.value,
            body = if (configured(SemanticField.ONLINE) || configured(SemanticField.ONLINE_OR_LOCATION) || onlineField != null) "" else normalizedInput.combined
        )

        val modeOnlyValue = combinedOnlineLocation?.value?.trim()?.lowercase(java.util.Locale.ROOT) in
            setOf("ort", "vor ort", "vor-ort", "onsite", "on site", "präsenz", "praesenz", "online", "remote", "hybrid", "unknown") ||
            combinedOnlineLocation != null && mappedMode != null
        val heuristicLocations = if (locationField == null && (combinedOnlineLocation == null || modeOnlyValue) && !configured(SemanticField.LOCATION) && !configured(SemanticField.CITY) && !configured(SemanticField.STREET)) {
            locationHeuristicExtractor.candidates(LocalHeuristicExtractor.context(heuristicInput))
        } else {
            emptyList()
        }
        val heuristicLocation = heuristicLocations.firstOrNull()
        if (heuristicLocations.map { it.value.lowercase() }.distinct().size > 1)
            issues += ExtractionIssue("ambiguous_heuristic_location", "Mehrere mögliche Orte im Mailtext. Zuordnung prüfen.", IssueSeverity.NEEDS_REVIEW)
        if (heuristicLocation != null && heuristicLocation.confidence < 0.70)
            issues += ExtractionIssue("uncertain_heuristic_location", "Ortsangabe ist nur schwach belegt. Bitte prüfen.", IssueSeverity.NEEDS_REVIEW)

        val addressParts = listOf(SemanticField.STREET, SemanticField.POSTAL_CODE, SemanticField.CITY)
            .mapNotNull { firstSemantic(semanticFields, it)?.value?.takeIf(String::isNotBlank) }
        val location = when {
            addressParts.isNotEmpty() -> (listOfNotNull(locationField?.value?.takeIf(String::isNotBlank)) + addressParts).distinct().joinToString(", ")
            locationField != null -> locationField.value
            combinedOnlineLocation != null && !modeOnlyValue &&
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
            val structuredConfidence = (
                dateFields.map { it.evidence.confidence } +
                    listOfNotNull(timeField?.evidence?.confidence)
                ).minOrNull()

            val timeConfidence = if (usesDateFallback || usesTimeFallback) minOf(structuredConfidence ?: 1.0, fallbackSources.confidence) else structuredConfidence ?: 0.82
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
                warnings = candidate.warnings + buildList {
                    if (usesDateFallback) add("heuristic_date")
                    if (usesTimeFallback) add("heuristic_time")
                    if (heuristicLocation != null) add("heuristic_location")
                },
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

        fun addGuess(semantic: SemanticField, value: String, evidence: String, confidence: Double) {
            val key = "__guess_" + semantic.name
            if (value.isNotBlank() && key !in fields) {
                val position = normalizedInput.combined.indexOf(evidence).takeIf { it >= 0 }
                fields[key] = ExtractedValue(key, semantic, value, ExtractionEvidence(EvidenceKind.HEURISTIC,
                    InputSource.COMBINED, null, evidence.take(240), confidence,
                    position, position?.plus(evidence.length)))
            }
        }
        if (usesDateFallback) candidates.firstOrNull()?.localDate?.let { addGuess(SemanticField.DATE, it.toString(), fallbackSources.evidence, fallbackSources.confidence) }
        if (usesTimeFallback) candidates.firstOrNull()?.localStartTime?.let { addGuess(SemanticField.TIME, it.toString(), fallbackSources.evidence, fallbackSources.confidence) }
        heuristicLocation?.let { addGuess(SemanticField.LOCATION, it.value, it.evidence, it.confidence) }

        return ExtractionResult(
            profileId = profile?.id,
            fields = fields,
            labels = labels.distinctBy { it.id },
            candidates = candidates,
            issues = issues.distinctBy { Triple(it.code, it.fieldKey, it.message) },
            description = profile?.extractors.orEmpty().filter { it.includeInDescription }.mapNotNull { rule ->
                fields[rule.key]?.value?.takeIf(String::isNotBlank)?.let { rule.displayName.ifBlank { rule.key } + ": " + it }
            }.joinToString("\n")
        )
    }

    private fun firstSemantic(
        fields: Map<String, ExtractedValue>,
        semantic: SemanticField
    ): ExtractedValue? = fields.values.firstOrNull { it.semantic == semantic }

    private fun semanticValues(
        fields: Map<String, ExtractedValue>,
        semantic: SemanticField
    ): List<ExtractedValue> = fields.values.filter { it.semantic == semantic }

    private fun structuredDateText(
        dateFields: List<ExtractedValue>,
        mode: MultipleDateMode
    ): String? {
        if (dateFields.isEmpty()) return null
        if (dateFields.size == 1) return dateFields.single().value

        val separator = when (mode) {
            MultipleDateMode.ALTERNATIVE -> " oder "
            MultipleDateMode.MULTIPLE_OPTIONS -> " und "
            MultipleDateMode.AUTO,
            MultipleDateMode.UNSPECIFIED -> " ; "
        }

        return dateFields.joinToString(separator) { it.value }
    }

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
