package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.RequestLabel

enum class LabelRuleSource {
    SENDER,
    SUBJECT,
    BODY,
    COMBINED
}

data class LabelRule(
    val id: String,
    val label: RequestLabel,
    val source: LabelRuleSource,
    val regex: String,
    val ignoreCase: Boolean = true
)

class LabelRuleEngine {
    fun labels(
        input: ExtractionInput,
        rules: List<LabelRule>
    ): List<RequestLabel> {
        return rules.mapNotNull { rule ->
            val source = when (rule.source) {
                LabelRuleSource.SENDER -> input.sender
                LabelRuleSource.SUBJECT -> input.subject
                LabelRuleSource.BODY -> input.body
                LabelRuleSource.COMBINED -> input.combined
            }

            val options = buildSet {
                if (rule.ignoreCase) add(RegexOption.IGNORE_CASE)
                add(RegexOption.MULTILINE)
            }

            val matches = runCatching {
                Regex(rule.regex, options).containsMatchIn(source)
            }.getOrDefault(false)

            rule.label.takeIf { matches }
        }.distinctBy { it.id }
            .sortedWith(
                compareByDescending<RequestLabel> { it.priority }
                    .thenBy { it.name.lowercase() }
            )
    }
}
