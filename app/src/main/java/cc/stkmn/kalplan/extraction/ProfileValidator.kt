package cc.stkmn.kalplan.extraction

data class ProfileValidationError(
    val code: String,
    val message: String,
    val ruleId: String? = null
)

class ProfileValidator {
    fun validate(profile: ExtractionProfile): List<ProfileValidationError> {
        val errors = mutableListOf<ProfileValidationError>()

        if (profile.schemaVersion != ExtractionProfile.CURRENT_SCHEMA_VERSION) {
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
            runCatching { Regex(matcher.regex) }.onFailure {
                errors += ProfileValidationError(
                    code = "invalid_matcher_regex",
                    message = "Invalid matcher regex.",
                    ruleId = matcher.id
                )
            }
        }

        val availableVariables = mutableSetOf("input", "body", "text", "subject", "sender")
        profile.extractors.forEach { rule ->
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

            runCatching {
                val regex = Regex(rule.regex)
                val groups = regex.toPattern().matcher("").groupCount()
                require(rule.group in 0..groups) { "Capture group does not exist." }
            }.onFailure {
                errors += ProfileValidationError(
                    code = "invalid_extractor_regex",
                    message = it.message ?: "Invalid extractor regex.",
                    ruleId = rule.id
                )
            }

            availableVariables += rule.key
        }

        return errors
    }

    private fun duplicateIds(values: List<String>): Set<String> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
