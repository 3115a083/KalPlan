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
    val sampleLabel: String? = null,
    val displayName: String = "",
    val dataType: ProfileFieldType = ProfileFieldType.TEXT,
    val selector: FieldSelector? = null,
    val variants: List<FieldSelector> = emptyList(),
    val additionalSemantics: Set<SemanticField> = emptySet(),
    val labelMappings: List<FieldLabelMapping> = emptyList(),
    val modeMappings: Map<String, String> = emptyMap(),
    val includeInDescription: Boolean = false,
    val allowHeuristicFallback: Boolean = true
)

@Serializable
enum class ProfileFieldType { TEXT, SECTION, DATE, TIME, DURATION, NUMBER, EMAIL }

@Serializable
enum class FieldSelectorKind { LABELED_LINE, BETWEEN, WHOLE, TABLE_COLUMN, REGEX }

/** Anchors are literal text. Only REGEX uses bounded RE2/J expressions. */
@Serializable
data class FieldSelector(
    val kind: FieldSelectorKind = FieldSelectorKind.LABELED_LINE,
    val anchor: String = "",
    val endAnchor: String = "",
    val separator: String = ":",
    val ignoreCase: Boolean = true,
    val column: Int = 1,
    val pattern: String = "",
    val group: Int = 1
)

@Serializable
data class FieldLabelMapping(val value: String, val label: String)

/** Examples stay in encrypted local app state and travel with an explicit profile export. */
@Serializable
data class ProfileExample(
    val id: String,
    val name: String,
    val sender: String = "",
    val subject: String = "",
    val body: String = "",
    val receivedMillis: Long,
    val zoneId: String = "Europe/Berlin",
    val expected: Map<String, String> = emptyMap(),
    val shouldMatch: Boolean = true
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
    val declineTemplate: String? = null,
    val examples: List<ProfileExample> = emptyList(),
    val extractionZoneId: String? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
