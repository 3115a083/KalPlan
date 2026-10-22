package cc.stkmn.kalplan.extraction

internal data class FieldMatch(val value: String, val matchedText: String, val start: Int, val end: Int)

internal object FieldSelectorEngine {
    fun find(source: String, selector: FieldSelector): List<FieldMatch> {
        require(source.length <= 512_000)
        if (selector.kind == FieldSelectorKind.TABLE_COLUMN) return table(source, selector)
        val regex = when (selector.kind) {
            FieldSelectorKind.LABELED_LINE -> "(?m)^[ \\t]*" + FieldRuleBuilder.literal(selector.anchor.trim()) +
                "[ \\t]*" + Regex.escape(selector.separator) + "[ \\t]*([^\\n]*)$"
            FieldSelectorKind.BETWEEN -> "(?s)" + FieldRuleBuilder.literal(selector.anchor) + "(.*?)" +
                (if (selector.endAnchor.isEmpty()) "$" else FieldRuleBuilder.literal(selector.endAnchor))
            FieldSelectorKind.WHOLE -> "(?s)^(.*)$"
            FieldSelectorKind.REGEX -> selector.pattern
            FieldSelectorKind.TABLE_COLUMN -> error("handled above")
        }
        val matcher = SafePattern.compile(regex, selector.ignoreCase).matcher(source)
        val group = if (selector.kind == FieldSelectorKind.REGEX) selector.group else 1
        require(group in 0..matcher.groupCount())
        return buildList {
            while (matcher.find() && size < 100) {
                val captured = matcher.group(group) ?: continue
                add(FieldMatch(captured, matcher.group(), matcher.start(group), matcher.end(group)))
            }
        }
    }

    private fun table(source: String, selector: FieldSelector): List<FieldMatch> {
        require(selector.column in 1..100 && selector.separator.isNotEmpty())
        val delimiter = if (selector.separator == "\\t") "\t" else selector.separator
        val lines = source.split('\n')
        val index = selector.column - 1
        val header = lines.indexOfFirst { line ->
            val cells = line.split(delimiter)
            cells.getOrNull(index)?.trim()?.equals(selector.anchor.trim(), selector.ignoreCase) == true
        }
        if (header < 0) return emptyList()
        var offset = lines.take(header + 1).sumOf { it.length + 1 }
        return buildList {
            for (line in lines.drop(header + 1)) {
                if (line.isBlank() || selector.endAnchor.isNotBlank() && line.contains(selector.endAnchor, selector.ignoreCase)) break
                val cells = line.split(delimiter)
                if (cells.size <= index) break
                val start = offset + cells.take(index).sumOf { it.length + delimiter.length }
                add(FieldMatch(cells[index], line, start, start + cells[index].length))
                offset += line.length + 1
                if (size >= 100) break
            }
        }
    }
}
