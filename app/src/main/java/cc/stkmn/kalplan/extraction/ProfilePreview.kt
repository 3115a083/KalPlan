package cc.stkmn.kalplan.extraction

import java.time.Instant
import java.time.ZoneId

data class ProfileExampleCheck(val name: String, val passed: Boolean, val details: List<String>)

object ProfilePreview {
    fun evaluate(input: ExtractionInput, profile: ExtractionProfile): ExtractionResult {
        val errors = ProfileValidator().validate(profile)
        if (errors.isNotEmpty()) return ExtractionResult(profile.id, emptyMap(), emptyList(), emptyList(), errors.map {
            ExtractionIssue(it.code, it.message, IssueSeverity.NEEDS_REVIEW, it.ruleId)
        })
        return KalPlanExtractionPipeline().extract(input, profile)
    }

    fun checkExamples(profile: ExtractionProfile): List<ProfileExampleCheck> = profile.examples.map { example ->
        val input = ExtractionInput(example.sender, example.subject, example.body, Instant.ofEpochMilli(example.receivedMillis), ZoneId.of(example.zoneId))
        val matches = ProfileExtractionEngine().matchingProfiles(MailTextNormalizer().normalize(input), listOf(profile.copy(enabled = true))).isNotEmpty()
        val details = mutableListOf<String>()
        if (matches != example.shouldMatch) details += if (example.shouldMatch) "Profil passt nicht mehr zum Beispiel." else "Negativbeispiel passt unerwartet zum Profil."
        if (example.shouldMatch) {
            val result = evaluate(input, profile)
            example.expected.forEach { (key, expected) ->
                val field = result.fields[key]
                if (field == null) details += "$key: nicht gefunden."
                else if (field.value != expected) details += "$key: erwartet '$expected', erkannt '${field.value}'."
            }
            result.issues.filter { it.severity == IssueSeverity.NEEDS_REVIEW }.forEach { details += it.message }
            if (example.expected.isEmpty()) details += "Noch keine erwarteten Feldwerte hinterlegt."
        }
        ProfileExampleCheck(example.name, details.isEmpty(), details)
    }
}
