package cc.stkmn.kalplan.extraction

class ProfileExtractionEngine {
    data class ProfileExtraction(
        val values: Map<String, ExtractedValue>,
        val issues: List<ExtractionIssue>
    )

    fun matchingProfiles(
        input: ExtractionInput,
        profiles: List<ExtractionProfile>
    ): List<ExtractionProfile> {
        val matched = profiles.filter { profile ->
            profile.enabled && profile.matchers.all { matcher ->
                val source = sourceFor(input, matcher.source)
                runCatching {
                    SafePattern.compile(matcher.regex, matcher.ignoreCase).matcher(source.take(512_000)).find()
                }.getOrDefault(false)
            }
        }

        val specific = matched.filter { it.matchers.isNotEmpty() }
        return if (specific.isNotEmpty()) specific else matched
    }

    fun extract(
        input: ExtractionInput,
        profile: ExtractionProfile
    ): ProfileExtraction {
        val values = linkedMapOf<String, ExtractedValue>()
        val rawValues = builtIns(input).toMutableMap()
        val issues = mutableListOf<ExtractionIssue>()

        for (rule in profile.extractors) {
            val variableKey = rule.sourceVariableKey?.takeIf { it.isNotBlank() }
            val source = if (variableKey != null) {
                rawValues[variableKey]
            } else {
                sourceFor(input, rule.source)
            }

            if (source == null) {
                if (rule.required) {
                    issues += ExtractionIssue(
                        code = "source_variable_missing",
                        message = "Source variable '" + variableKey + "' was not extracted.",
                        severity = IssueSeverity.NEEDS_REVIEW,
                        fieldKey = rule.key
                    )
                }
                continue
            }

            val result = try {
                extractOne(source, rule, profile.parseDirection)
            } catch (error: Exception) {
                issues += ExtractionIssue(
                    code = "invalid_rule",
                    message = "Extractor '" + rule.key + "' failed: " +
                        error::class.simpleName.orEmpty(),
                    severity = IssueSeverity.NEEDS_REVIEW,
                    fieldKey = rule.key
                )
                null
            }

            if (result == null) {
                if (rule.required) {
                    issues += ExtractionIssue(
                        code = "required_field_missing",
                        message = "Required field '" + rule.key + "' could not be extracted.",
                        severity = IssueSeverity.NEEDS_REVIEW,
                        fieldKey = rule.key
                    )
                }
                continue
            }

            rawValues[rule.key] = result.value
            values[rule.key] = ExtractedValue(
                key = rule.key,
                semantic = rule.semantic,
                value = result.value,
                evidence = ExtractionEvidence(
                    kind = EvidenceKind.PROFILE_RULE,
                    source = rule.source,
                    ruleId = rule.id,
                    matchedText = result.matchedText.take(240),
                    confidence = rule.confidence.coerceIn(0.0, 1.0),
                    startIndex = result.startIndex,
                    endIndexExclusive = result.endIndexExclusive
                )
            )
        }

        return ProfileExtraction(values = values, issues = issues)
    }

    private data class ExtractOneResult(
        val value: String,
        val matchedText: String,
        val startIndex: Int?,
        val endIndexExclusive: Int?
    )

    private fun extractOne(
        input: String,
        rule: ExtractorRule,
        direction: ParseDirection
    ): ExtractOneResult? {
        val matcher = SafePattern.compile(rule.regex).matcher(input.take(512_000))
        var found = false
        var captured: String? = null
        var matched = ""
        var start = 0
        var end = 0
        while (matcher.find()) {
            require(rule.group in 0..matcher.groupCount()) { "Capture group does not exist" }
            captured = matcher.group(rule.group)
            matched = matcher.group()
            start = matcher.start(rule.group)
            end = matcher.end(rule.group)
            found = true
            if (direction == ParseDirection.TOP_DOWN) break
        }
        if (!found || captured == null) return null
        var value = captured
        for (transform in rule.transforms) value = applyTransform(requireNotNull(value), transform).also {
            require(it.length <= 512_000) { "Transform output limit" }
        }

        return ExtractOneResult(
            value = requireNotNull(value),
            matchedText = matched,
            startIndex = start,
            endIndexExclusive = end
        )
    }

    private fun applyTransform(value: String, transform: ValueTransform): String = when (transform) {
        ValueTransform.Trim -> value.trim()
        is ValueTransform.Prefix -> transform.value + value
        is ValueTransform.Suffix -> value + transform.value
        is ValueTransform.ChangeCase -> when (transform.mode) {
            CaseMode.LOWER -> value.lowercase()
            CaseMode.UPPER -> value.uppercase()
        }
        is ValueTransform.RegexReplace -> {
            if (transform.regex.isEmpty()) value
            else {
                val pattern = if (transform.literal) Regex.escape(transform.regex) else transform.regex
                SafePattern.compile(pattern, transform.ignoreCase).matcher(value).replaceAll(transform.replacement)
            }
        }
    }

    private fun builtIns(input: ExtractionInput): Map<String, String> = mapOf(
        "input" to input.combined,
        "body" to input.body,
        "text" to input.body,
        "subject" to input.subject,
        "sender" to input.sender
    )

    private fun sourceFor(input: ExtractionInput, source: InputSource): String = when (source) {
        InputSource.COMBINED -> input.combined
        InputSource.BODY -> input.body
        InputSource.SUBJECT -> input.subject
        InputSource.SENDER -> input.sender
    }
}

