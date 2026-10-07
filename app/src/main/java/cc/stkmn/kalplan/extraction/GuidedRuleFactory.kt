package cc.stkmn.kalplan.extraction

object GuidedRuleFactory {
    data class Candidate(
        val label: String,
        val value: String,
        val source: InputSource,
        val sourceLine: String,
        val suggestedKey: String
    )

    fun candidates(input: ExtractionInput): List<Candidate> = buildList {
        if (input.subject.isNotBlank()) {
            add(
                Candidate(
                    label = "Subject",
                    value = input.subject.trim(),
                    source = InputSource.SUBJECT,
                    sourceLine = input.subject.trim(),
                    suggestedKey = "subject"
                )
            )
        }

        input.body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_GUIDED_LINES)
            .forEachIndexed { index, line ->
                val split = splitLabelAndValue(line)
                add(
                    Candidate(
                        label = split?.first ?: "Line " + (index + 1),
                        value = split?.second ?: line,
                        source = InputSource.BODY,
                        sourceLine = line,
                        suggestedKey = suggestedKey(split?.first ?: "field_" + (index + 1))
                    )
                )
            }
    }.distinctBy { Triple(it.source, it.sourceLine, it.value) }

    fun extractor(
        candidate: Candidate,
        key: String = candidate.suggestedKey,
        semantic: SemanticField = SemanticField.CUSTOM,
        required: Boolean = false
    ): ExtractorRule {
        val normalizedKey = sanitizeKey(key).ifBlank { candidate.suggestedKey }

        if (candidate.source == InputSource.SUBJECT) {
            return ExtractorRule(
                id = "guided_" + normalizedKey,
                key = normalizedKey,
                semantic = semantic,
                regex = "(?s)^\\s*(.+?)\\s*$",
                required = required,
                source = InputSource.SUBJECT,
                sampleLabel = candidate.label
            )
        }

        val selectedIndex = candidate.sourceLine.indexOf(candidate.value)
        if (selectedIndex >= 0 && candidate.value != candidate.sourceLine) {
            return extractorFromSelection(
                sourceText = candidate.sourceLine,
                selectionStart = selectedIndex,
                selectionEnd = selectedIndex + candidate.value.length,
                key = normalizedKey,
                semantic = semantic,
                source = candidate.source,
                required = required
            ).copy(sampleLabel = candidate.label)
        }

        val split = splitLabelAndValue(candidate.sourceLine)
        return if (split != null) {
            val label = Regex.escape(split.first)
            val separator = Regex.escape(separatorOf(candidate.sourceLine))
            ExtractorRule(
                id = "guided_" + normalizedKey,
                key = normalizedKey,
                semantic = semantic,
                regex = "(?m)^\\s*" + label + "\\s*" + separator + "\\s*(.+?)\\s*$",
                required = required,
                source = InputSource.BODY,
                sampleLabel = split.first
            )
        } else {
            ExtractorRule(
                id = "guided_" + normalizedKey,
                key = normalizedKey,
                semantic = semantic,
                regex = "(?m)^\\s*(" + Regex.escape(candidate.sourceLine) + ")\\s*$",
                required = required,
                source = InputSource.BODY,
                sampleLabel = candidate.label
            )
        }
    }

    fun extractorFromSelection(
        sourceText: String,
        selectionStart: Int,
        selectionEnd: Int,
        key: String,
        semantic: SemanticField,
        source: InputSource,
        required: Boolean = false
    ): ExtractorRule {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
        require(end > start) { "A text selection is required." }

        val lineStart = sourceText.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val lineEnd = sourceText.indexOf('\n', end)
            .let { if (it < 0) sourceText.length else it }

        val prefix = sourceText.substring(lineStart, start)
        val suffix = sourceText.substring(end, lineEnd)
        val selected = sourceText.substring(start, end)
        val normalizedKey = sanitizeKey(key)

        val regex = if (prefix.isBlank() && suffix.isBlank()) {
            "(?m)^\\s*(.+?)\\s*$"
        } else {
            "(?m)^" + flexibleLiteral(prefix) + "(.+?)" + flexibleLiteral(suffix) + "$"
        }

        return ExtractorRule(
            id = "guided_" + normalizedKey,
            key = normalizedKey,
            semantic = semantic,
            regex = regex,
            required = required,
            source = source,
            sampleLabel = selected.take(80)
        )
    }

    private fun splitLabelAndValue(line: String): Pair<String, String>? {
        for (separator in SEPARATORS) {
            val index = line.indexOf(separator)
            if (index in 1 until line.lastIndex) {
                val left = line.substring(0, index).trim()
                val right = line.substring(index + separator.length).trim()
                if (left.length <= 60 && right.isNotBlank()) return left to right
            }
        }
        return null
    }

    private fun separatorOf(line: String): String =
        SEPARATORS.firstOrNull { it in line } ?: ":"

    private fun flexibleLiteral(value: String): String {
        if (value.isEmpty()) return ""
        return value
            .split(Regex("[\\s\\p{Z}]+"))
            .filter { it.isNotEmpty() }
            .joinToString("[\\s\\p{Z}]+") { Regex.escape(it) }
    }

    fun sanitizeKey(value: String): String = value
        .trim()
        .lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9_.-]+"), "_")
        .trim('_')

    private fun suggestedKey(label: String): String =
        sanitizeKey(label).ifBlank { "field" }

    private const val MAX_GUIDED_LINES = 120
    private val SEPARATORS = listOf(":", "=", "–", "—")
}
