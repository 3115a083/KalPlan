package cc.stkmn.kalplan.extraction

enum class CaseMode {
    LOWER,
    UPPER
}

sealed interface ValueTransform {
    data object Trim : ValueTransform
    data class Prefix(val value: String) : ValueTransform
    data class Suffix(val value: String) : ValueTransform
    data class ChangeCase(val mode: CaseMode) : ValueTransform
    data class RegexReplace(
        val regex: String,
        val replacement: String,
        val ignoreCase: Boolean = false,
        val literal: Boolean = false
    ) : ValueTransform
}

data class MatcherRule(
    val id: String,
    val regex: String,
    val source: InputSource = InputSource.COMBINED,
    val ignoreCase: Boolean = true
)

data class ExtractorRule(
    val id: String,
    val key: String,
    val semantic: SemanticField = SemanticField.CUSTOM,
    val regex: String,
    val group: Int = 1,
    val required: Boolean = false,
    val source: InputSource = InputSource.BODY,
    val sourceVariableKey: String? = null,
    val transforms: List<ValueTransform> = listOf(ValueTransform.Trim),
    val confidence: Double = 0.98,
    val sampleLabel: String? = null
)

data class ExtractionProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matchers: List<MatcherRule> = emptyList(),
    val extractors: List<ExtractorRule> = emptyList(),
    val parseDirection: ParseDirection = ParseDirection.TOP_DOWN,
    val defaultDurationMinutes: Int? = null,
    val locale: TemporalLocale = TemporalLocale.DE_DE
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
