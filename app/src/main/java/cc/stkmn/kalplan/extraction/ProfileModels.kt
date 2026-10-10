package cc.stkmn.kalplan.extraction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CaseMode {
    LOWER,
    UPPER
}

@Serializable
sealed interface ValueTransform {
    @Serializable
    @SerialName("trim")
    data object Trim : ValueTransform
    @Serializable
    @SerialName("prefix")
    data class Prefix(val value: String) : ValueTransform
    @Serializable
    @SerialName("suffix")
    data class Suffix(val value: String) : ValueTransform
    @Serializable
    @SerialName("change_case")
    data class ChangeCase(val mode: CaseMode) : ValueTransform
    @Serializable
    @SerialName("regex_replace")
    data class RegexReplace(
        val regex: String,
        val replacement: String,
        val ignoreCase: Boolean = false,
        val literal: Boolean = false
    ) : ValueTransform
}

@Serializable
data class MatcherRule(
    val id: String,
    val regex: String,
    val source: InputSource = InputSource.COMBINED,
    val ignoreCase: Boolean = true
)

@Serializable
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

@Serializable
enum class MultipleDateMode {
    AUTO,
    ALTERNATIVE,
    MULTIPLE_OPTIONS,
    UNSPECIFIED
}

@Serializable
data class ExtractionProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matchers: List<MatcherRule> = emptyList(),
    val extractors: List<ExtractorRule> = emptyList(),
    val parseDirection: ParseDirection = ParseDirection.TOP_DOWN,
    val defaultDurationMinutes: Int? = null,
    val locale: TemporalLocale = TemporalLocale.DE_DE,
    val multipleDateMode: MultipleDateMode = MultipleDateMode.AUTO,
    val acceptTemplate: String? = null,
    val declineTemplate: String? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
