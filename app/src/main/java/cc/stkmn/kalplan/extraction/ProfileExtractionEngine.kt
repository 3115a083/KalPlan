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
                    Regex(
                        matcher.regex,
                        buildSet {
                            add(RegexOption.MULTILINE)
                            if (matcher.ignoreCase) add(RegexOption.IGNORE_CASE)
                        }
                    ).containsMatchIn(source)
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
            val source = rule.sourceVariableKey
                ?.takeIf { it.isNotBlank() }
                ?.let(rawValues::get)
                ?: sourceFor(input, rule.source)

            val result = try {
                extractOne(source, rule, profile.parseDirection)
            } catch (error: Exception) {
                issues += ExtractionIssue(
                    code = "invalid_rule",
                    message = "Extractor '" + rule.key + "' failed: " +
                        (error.message ?: error::class.simpleName.orEmpty()),
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
        val regex = Regex(rule.regex, setOf(RegexOption.MULTILINE))
        val match = when (direction) {
            ParseDirection.TOP_DOWN -> regex.find(input)
            ParseDirection.BOTTOM_UP -> regex.findAll(input).lastOrNull()
        } ?: return null

        require(rule.group in match.groupValues.indices) {
            "Capture group " + rule.group + " does not exist"
        }

        val group = match.groups[rule.group] ?: return null
        var value = group.value
        for (transform in rule.transforms) {
            value = applyTransform(value, transform)
        }

        return ExtractOneResult(
            value = value,
            matchedText = match.value,
            startIndex = group.range.first,
            endIndexExclusive = group.range.last + 1
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
                val options = if (transform.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                val pattern = if (transform.literal) Regex.escape(transform.regex) else transform.regex
                Regex(pattern, options).replace(value, transform.replacement)
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
