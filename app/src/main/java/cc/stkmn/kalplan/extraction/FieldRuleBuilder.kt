package cc.stkmn.kalplan.extraction

import java.util.Locale
import java.util.UUID

/** Creates editable rules, never stores a selected example value as a future answer. */
object FieldRuleBuilder {
    fun fromSelection(inputText: String, start: Int, end: Int, source: InputSource, key: String): FieldSelector {
        val a = minOf(start, end).coerceIn(0, inputText.length)
        val b = maxOf(start, end).coerceIn(0, inputText.length)
        require(b > a) { "Markiere zuerst den gewünschten Wert." }
        val marker = "KALPLAN_" + UUID.randomUUID().toString() + "_"
        val begin = marker + "BEGIN"
        val finish = marker + "END"
        val tagged = inputText.substring(0, a) + begin + inputText.substring(a, b) + finish + inputText.substring(b)
        val normalized = MailTextNormalizer().normalizeText(tagged, source != InputSource.SUBJECT && source != InputSource.SENDER)
        val normalizedStart = normalized.indexOf(begin)
        val normalizedEnd = normalized.indexOf(finish) - begin.length
        val text = normalized.replace(begin, "").replace(finish, "")
        return fromNormalizedSelection(text, normalizedStart, normalizedEnd, source, key)
    }

    private fun fromNormalizedSelection(inputText: String, start: Int, end: Int, source: InputSource, key: String): FieldSelector {
        require(key.isNotBlank() && source in InputSource.entries)
        val a = minOf(start, end).coerceIn(0, inputText.length)
        val b = maxOf(start, end).coerceIn(0, inputText.length)
        require(b > a) { "Markiere zuerst den gewünschten Wert." }
        val lineStart = if (a == 0) 0 else inputText.lastIndexOf('\n', a - 1) + 1
        val lineEnd = inputText.indexOf('\n', b).let { if (it < 0) inputText.length else it }
        val before = inputText.substring(lineStart, a)
        val after = inputText.substring(b, lineEnd)
        if (!inputText.substring(a, b).contains('\n')) {
            require(before.isNotBlank() || after.isNotBlank()) {
                "Für eine ganze Zeile eine Bezeichnung oder begrenzende Textanker festlegen."
            }
            return FieldSelector(kind = FieldSelectorKind.REGEX,
                pattern = "(?m)^" + literal(before) + "([^\\n]*?)" + literal(after) + "$", group = 1)
        }
        val previousLine = inputText.substring(0, lineStart).trimEnd('\n').substringAfterLast('\n')
        val nextLine = inputText.substring(lineEnd).trimStart('\n').substringBefore('\n')
        val anchor = before.ifBlank { previousLine + "\n" }
        val endAnchor = after.ifBlank { if (nextLine.isNotBlank()) "\n" + nextLine else "" }
        require(anchor.isNotBlank() && endAnchor.isNotBlank()) {
            "Für Abschnitte einen Anfangs- und Endanker festlegen."
        }
        return FieldSelector(kind = FieldSelectorKind.BETWEEN, anchor = anchor, endAnchor = endAnchor)
    }

    fun suggestions(input: ExtractionInput): List<ExtractorRule> {
        val normalized = MailTextNormalizer().normalize(input)
        return normalized.body.lineSequence().take(120).mapNotNull { line ->
            val match = Regex("^([^:=\\n]{1,80})[ \\t]*([:=])[ \\t]*(.*)$").find(line) ?: return@mapNotNull null
            val name = match.groupValues[1].trim()
            val semantic = when (name.lowercase(Locale.ROOT)) {
                "datum", "date", "termin", "einsatzdatum" -> SemanticField.DATE
                "startzeit", "beginn", "start", "start time", "uhrzeit" -> SemanticField.TIME
                "endzeit", "ende", "end", "end time" -> SemanticField.END_TIME
                "dauer", "duration" -> SemanticField.DURATION
                "ort", "location", "einsatzort", "adresse", "address" -> SemanticField.LOCATION
                "plz", "postcode", "postal code" -> SemanticField.POSTAL_CODE
                "stadt", "city" -> SemanticField.CITY
                "straße, hausnummer", "straße", "strasse", "street" -> SemanticField.STREET
                "ort oder online", "terminart", "teilnahmeart", "meeting mode" -> SemanticField.ONLINE
                else -> SemanticField.CUSTOM
            }
            val key = GuidedRuleFactory.sanitizeKey(name).take(80).ifBlank { "information" }.let {
                if (it in setOf("input", "body", "text", "subject", "sender")) it + "_wert" else it
            }
            ExtractorRule(id = UUID.randomUUID().toString(), key = key,
                semantic = semantic, regex = "(.*)", source = InputSource.BODY,
                displayName = name, sampleLabel = name,
                dataType = when (semantic) {
                    SemanticField.DATE -> ProfileFieldType.DATE
                    SemanticField.TIME, SemanticField.END_TIME -> ProfileFieldType.TIME
                    SemanticField.DURATION -> ProfileFieldType.DURATION
                    else -> ProfileFieldType.TEXT
                },
                selector = FieldSelector(anchor = name, separator = match.groupValues[2]),
                modeMappings = if (semantic == SemanticField.ONLINE) mapOf("Ort" to "ONSITE", "Vor Ort" to "ONSITE", "Online" to "ONLINE", "Hybrid" to "HYBRID") else emptyMap())
        }.distinctBy { it.selector?.anchor?.lowercase(Locale.ROOT) }.take(100).toList()
    }

    internal fun literal(text: String): String = text.split(Regex("[\\p{Zs}\\t]+"))
        .joinToString("[\\p{Zs}\\t]*") { Regex.escape(it) }
}
