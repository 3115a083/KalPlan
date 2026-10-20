package cc.stkmn.kalplan.extraction

import java.time.ZonedDateTime

/** Scores are local ranking heuristics, not calibrated probabilities. Sources retain original wording. */
data class TemporalFallback(
    val dateText: String,
    val timeText: String,
    val confidence: Double,
    val ambiguous: Boolean,
    val evidence: String
)

object LocalHeuristicExtractor {
    private val replyMarker = Regex("(?iu)^(?:on\\b.{0,300}\\bwrote:|am\\b.{0,300}\\bschrieb.{0,100}:|[-_ ]*(?:original message|ursprüngliche nachricht|weitergeleitete nachricht|forwarded message)[-_ :]*$)")
    private val fromHeader = Regex("(?iu)^(?:from|von):\\s*.+")
    private val sentHeader = Regex("(?iu)^(?:sent|gesendet):\\s*.+")
    private val cue = Regex("(?iu)\\b(?:termin|appointment|meeting|einsatz|auftrag|beginn|uhrzeit|datum|date|zeit|time|wann|start)\\b")
    private val unrelated = Regex("(?iu)\\b(?:rechnung|invoice|geburtstag|birthday|rechnungsdatum|zahlungsfrist|payment due)\\b")
    private val alternative = Regex("(?iu)^\\s*(?:oder|or|alternativ|alternatively|und|and)\\b")
    private val dateMention = Regex(
        "(?iu)(?<!\\d)(?:\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[./]\\d{1,2}(?:[./]\\d{2,4})?\\.?|\\d{1,2}-\\d{1,2}-\\d{2,4})(?!\\d)" +
            "|\\b(?:übermorgen|uebermorgen|morgen|heute|day after tomorrow|tomorrow|today|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b" +
            "|(?<!\\d)\\d{1,2}\\.?\\s+(?:januar|jan|februar|feb|märz|maerz|mrz|april|apr|mai|juni|jun|juli|jul|august|aug|september|sep|sept|oktober|okt|november|nov|dezember|dez|january|february|march|mar|may|june|july|october|oct|december|dec)(?:\\s+\\d{2,4})?\\b" +
            "|\\b(?:january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|sept|october|oct|november|nov|december|dec)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{2,4})?\\b"
    )
    private val timeMention = Regex(
        "(?iu)(?<![\\p{L}\\d])\\d{1,2}(?:[:.][0-5]\\d)?\\s*(?:am|pm|uhr)?\\s*(?:-|–|—|bis|to)\\s*\\d{1,2}(?:[:.][0-5]\\d)?\\s*(?:am|pm|uhr)?(?!\\d)" +
            "|(?<![\\p{L}\\d])\\d{1,2}[:.][0-5]\\d\\s*(?:am|pm|uhr)?(?!\\d)" +
            "|(?<![\\p{L}\\d])\\d{1,2}\\s*(?:am|pm|uhr)\\b"
    )

    /** Remove visible reply history only. User-defined fields/comments are intentionally retained. */
    fun context(input: ExtractionInput): String {
        val lines = input.body.take(20_000).replace("\r\n", "\n").replace('\r', '\n').lines().take(300)
        val current = buildList {
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed == "--" || replyMarker.containsMatchIn(trimmed)) break
                if (fromHeader.matches(trimmed) && lines.drop(index + 1).take(5).any { sentHeader.matches(it.trim()) }) break
                if (!trimmed.startsWith('>')) add(line)
            }
        }.joinToString("\n").trim()
        return listOf(input.subject.take(2000).trim(), current).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun temporalSources(input: ExtractionInput, locale: TemporalLocale): TemporalFallback {
        val reference = ZonedDateTime.ofInstant(input.receivedAt, input.zoneId)
        val parser = FlexibleTemporalParser()
        val validDates = mutableMapOf<String, Boolean>()
        val parsedTimes = mutableMapOf<String, Pair<java.time.LocalTime?, java.time.LocalTime?>?>()
        fun dates(text: String) = dateMention.findAll(text).filter { match ->
            validDates.getOrPut(match.value) {
                parser.parse(match.value, timeText = "", locale = locale, reference = reference).candidates.isNotEmpty()
            }
        }.toList()
        fun times(text: String): Set<Pair<java.time.LocalTime?, java.time.LocalTime?>> {
            val chars = text.toCharArray()
            dates(text).forEach { match -> match.range.forEach { chars[it] = ' ' } }
            return timeMention.findAll(chars.concatToString()).mapNotNull { match ->
                // The internal anchor only probes a time expression. It is never returned as a source.
                if (parsedTimes.containsKey(match.value)) parsedTimes[match.value] else {
                    val candidate = parser.parse(reference.toLocalDate().toString(), match.value, locale = locale, reference = reference).candidates.singleOrNull()
                    candidate?.takeIf { it.startTime != null }?.let { it.startTime to it.endTime }.also { parsedTimes[match.value] = it }
                }
            }.toSet()
        }
        data class Block(val raw: String, val dates: Boolean, val times: Set<Pair<java.time.LocalTime?, java.time.LocalTime?>>, val score: Int)
        val currentContext = context(input)
        val chunks = currentContext.split(Regex("\n[ \\t]*\n+"))
            .flatMap { it.split(Regex("(?<=[.!?]) +(?=[\\p{Lu}])")) }.flatMap { paragraph ->
            // Repeated date/time records remain separate even when the sender omitted blank lines.
            val records = mutableListOf<String>()
            var current = mutableListOf<String>()
            for (line in paragraph.lines()) {
                val prior = current.joinToString("\n")
                if (dates(line).isNotEmpty() && dates(prior).isNotEmpty() && times(prior).isNotEmpty() && !alternative.containsMatchIn(line)) {
                    records += prior
                    current = mutableListOf()
                }
                current += line
            }
            if (current.isNotEmpty()) records += current.joinToString("\n")
            records
        }
        fun block(raw: String): Block {
            val hasDates = dates(raw).isNotEmpty()
            val foundTimes = times(raw)
            val score = (if (hasDates) 3 else 0) + (if (foundTimes.isNotEmpty()) 3 else 0) +
                (if (cue.containsMatchIn(raw)) 4 else 0) - (if (unrelated.containsMatchIn(raw)) 6 else 0)
            return Block(raw, hasDates, foundTimes, score)
        }
        val base = chunks.filter(String::isNotBlank).map(::block)
        val neighboring = base.zipWithNext().mapNotNull { (first, second) ->
            val complementary = (first.dates && first.times.isEmpty() && !second.dates && second.times.isNotEmpty()) ||
                (!first.dates && first.times.isNotEmpty() && second.dates && second.times.isEmpty())
            if (complementary && !unrelated.containsMatchIn(first.raw + " " + second.raw)) block(first.raw + "\n\n" + second.raw) else null
        }
        val blocks = (base + neighboring).filter { (it.dates || it.times.isNotEmpty()) && it.score >= 0 }.sortedByDescending { it.score }
        val best = blocks.firstOrNull() ?: return TemporalFallback("", "", 0.0, false, "")
        val relevant = blocks.filter { it.score >= best.score - 3 && it.score >= 0 }
        // Alternative paragraphs belong together; unrelated competing records require review instead.
        val selected = (listOf(best) + relevant.filter { it !== best && alternative.containsMatchIn(it.raw) }).distinct()
        val competitors = relevant.filter { it !in selected && it.dates && it.times.isNotEmpty() }
        val ambiguous = relevant.flatMap { it.times }.distinct().size > 1 || competitors.isNotEmpty()
        val selectedText = selected.sortedBy { currentContext.indexOf(it.raw) }.joinToString("\n\n") { it.raw }
        val dateSource = if (selected.any { it.dates }) selectedText else ""
        val timeSource = if (selected.any { it.times.isNotEmpty() }) selectedText else ""
        val confidence = (if (best.dates && best.times.isNotEmpty()) 0.76 else 0.60).let { if (ambiguous) minOf(it, 0.48) else it }
        return TemporalFallback(dateSource, timeSource, confidence, ambiguous,
            (listOf(selectedText) + competitors.map { it.raw }).joinToString("\n\n").take(480))
    }
}
