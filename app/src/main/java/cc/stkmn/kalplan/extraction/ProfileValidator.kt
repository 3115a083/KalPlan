package cc.stkmn.kalplan.extraction

data class ProfileValidationError(
    val code: String,
    val message: String,
    val ruleId: String? = null
)

class ProfileValidator {
    fun validate(profile: ExtractionProfile): List<ProfileValidationError> {
        val errors = mutableListOf<ProfileValidationError>()
        if (profile.extractionZoneId != null && runCatching { java.time.ZoneId.of(profile.extractionZoneId) }.isFailure)
            errors += ProfileValidationError("zone_id", "Ungültige Zeitzone.")
        if (profile.examples.size > 10 || profile.examples.any { it.body.length > 20_000 || it.subject.length > 2000 || it.sender.length > 512 || it.expected.size > 100 || it.expected.any { e -> e.value.length > 20_000 } || runCatching { java.time.ZoneId.of(it.zoneId) }.isFailure })
            errors += ProfileValidationError("example_limit", "Maximal 10 Beispiele mit je 20.000 Zeichen und gültiger Zeitzone.")
        if (listOf(profile.acceptTemplate, profile.declineTemplate).any { it != null && it.length > 100_000 })
            errors += ProfileValidationError("reply_template_limit", "Reply template exceeds size limit.")
        if (profile.matchers.size > 100 || profile.extractors.size > 100)
            return listOf(ProfileValidationError("rule_limit", "Maximum 100 rules per profile."))
        if (profile.defaultDurationMinutes != null && profile.defaultDurationMinutes !in 1..10080)
            errors += ProfileValidationError("duration", "Invalid default duration.")

        if (profile.schemaVersion !in 1..ExtractionProfile.CURRENT_SCHEMA_VERSION) {
            errors += ProfileValidationError(
                code = "schema_version",
                message = "Unsupported profile schema version."
            )
        }
        if (profile.id.isBlank()) {
            errors += ProfileValidationError("profile_id", "Profile id must not be blank.")
        }
        if (profile.name.isBlank()) {
            errors += ProfileValidationError("profile_name", "Profile name must not be blank.")
        }

        duplicateIds(profile.matchers.map { it.id }).forEach {
            errors += ProfileValidationError("duplicate_matcher_id", "Duplicate matcher id: " + it, it)
        }
        duplicateIds(profile.extractors.map { it.id }).forEach {
            errors += ProfileValidationError("duplicate_extractor_id", "Duplicate extractor id: " + it, it)
        }
        duplicateIds(profile.extractors.map { it.key }).forEach {
            errors += ProfileValidationError("duplicate_extractor_key", "Duplicate extractor key: " + it)
        }

        profile.matchers.forEach { matcher ->
            runCatching { SafePattern.compile(matcher.regex) }.onFailure {
                errors += ProfileValidationError(
                    code = "invalid_matcher_regex",
                    message = "Invalid matcher regex.",
                    ruleId = matcher.id
                )
            }
        }

        val availableVariables = mutableSetOf("input", "body", "text", "subject", "sender")
        profile.extractors.forEach { rule ->
            if (rule.displayName.length > 200 || rule.variants.size > 20 || rule.labelMappings.size > 100 || rule.modeMappings.size > 100 ||
                rule.labelMappings.any { it.value.isBlank() || it.label.isBlank() || it.value.length > 1000 || it.label.length > 200 } ||
                rule.modeMappings.any { it.key.isBlank() || it.key.length > 1000 || it.value !in setOf("ONLINE", "ONSITE", "HYBRID", "UNKNOWN") })
                errors += ProfileValidationError("field_limit", "Feldname, Varianten oder Wertzuordnungen ungültig.", rule.id)
            if (rule.labelMappings.groupBy { it.value.trim().lowercase(java.util.Locale.ROOT) }.any { (_, entries) -> entries.map { it.label }.distinct().size > 1 } ||
                rule.modeMappings.entries.groupBy { it.key.trim().lowercase(java.util.Locale.ROOT) }.any { (_, entries) -> entries.map { it.value }.distinct().size > 1 })
                errors += ProfileValidationError("mapping_conflict", "Widersprüchliche Wertzuordnungen.", rule.id)
            (listOfNotNull(rule.selector) + rule.variants).forEach { selector ->
                runCatching {
                    require(selector.anchor.length <= 1000 && selector.endAnchor.length <= 1000 && selector.separator.length <= 20)
                    when (selector.kind) {
                        FieldSelectorKind.LABELED_LINE -> require(selector.anchor.isNotBlank() && selector.separator.isNotEmpty())
                        FieldSelectorKind.BETWEEN -> require(selector.anchor.isNotBlank())
                        FieldSelectorKind.TABLE_COLUMN -> require(selector.anchor.isNotBlank() && selector.column in 1..100 && selector.separator.isNotEmpty())
                        FieldSelectorKind.REGEX -> require(selector.group in 0..SafePattern.compile(selector.pattern, selector.ignoreCase).matcher("").groupCount())
                        FieldSelectorKind.WHOLE -> Unit
                    }
                    FieldSelectorEngine.find("", selector)
                }.onFailure { errors += ProfileValidationError("invalid_selector", "Feldquelle, Anker oder Spalte ungültig.", rule.id) }
            }
            if (rule.key.isBlank()) {
                errors += ProfileValidationError("blank_extractor_key", "Extractor key is blank.", rule.id)
            }
            if (rule.confidence !in 0.0..1.0) {
                errors += ProfileValidationError("confidence_range", "Confidence must be between 0 and 1.", rule.id)
            }
            if (rule.sourceVariableKey != null && rule.sourceVariableKey !in availableVariables) {
                errors += ProfileValidationError(
                    "unknown_source_variable",
                    "Source variable is not available before this extractor.",
                    rule.id
                )
            }

            if (rule.selector == null) runCatching {
                val regex = SafePattern.compile(rule.regex)
                val groups = regex.matcher("").groupCount()
                require(rule.group in 0..groups) { "Capture group does not exist." }
            }.onFailure {
                errors += ProfileValidationError(
                    code = "invalid_extractor_regex",
                    message = it.message ?: "Invalid extractor regex.",
                    ruleId = rule.id
                )
            }

            rule.transforms.forEach { transform ->
                if (transform is ValueTransform.RegexReplace && transform.regex.isNotEmpty()) {
                    runCatching { SafePattern.compile(if (transform.literal) Regex.escape(transform.regex) else transform.regex) }.onFailure {
                        errors += ProfileValidationError("invalid_transform_regex", "Unsupported transform regex.", rule.id)
                    }
                }
            }
            availableVariables += rule.key
        }

        return errors
    }

    private fun duplicateIds(values: List<String>): Set<String> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}

