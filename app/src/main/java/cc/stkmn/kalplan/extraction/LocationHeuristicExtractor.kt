package cc.stkmn.kalplan.extraction

data class LocationHeuristicResult(
    val value: String,
    val confidence: Double,
    val evidence: String
)

class LocationHeuristicExtractor {
    private val streetAddress = Regex(
        "(?iu)(?:\\b(?:am|an der|auf der|unter den|zum|zur)\\s+)?" +
            "[\\p{L}][\\p{L}.'’/-]*(?:straße|strasse|str\\.?|weg|allee|platz|gasse|ring|ufer|chaussee|damm|steig|stieg|pfad|promenade)" +
            "[ \\t]+\\d{1,5}[a-zA-Z]?(?:[ \\t]*[-/][ \\t]*\\d{1,5}[a-zA-Z]?)?"
    )
    private val postalCity = Regex(
        "(?<!\\d)\\d{5}[ \\t]+[\\p{L}][\\p{L}'’-]*" +
            "(?:[ \\t]+(?:am|an|der|im|vor|bei|ob|[\\p{Lu}][\\p{L}'’-]*)){0,4}"
    )
    private val cityPhrase = Regex(
        "\\b(?:in|bei|at)[ \\t]+([\\p{Lu}][\\p{L}.'’-]{2,}(?:[ \\t]+[\\p{Lu}][\\p{L}.'’-]{2,}){0,2})"
    )
    private val explicitCity = Regex("(?iu)^\\s*(?:ort|stadt|city|location)[ \\t]*:[ \\t]*([\\p{L}][\\p{L} .'-]{1,70})$")
    private val distinctAddressLabel = Regex("(?iu)\\b(?:rechnungsadresse|billing|versandadresse|lieferadresse|absender|sender)\\b")

    fun extract(text: String): LocationHeuristicResult? = candidates(text).firstOrNull()

    /** Pair only local fragments. A later, unrelated postal address never completes an earlier street. */
    fun candidates(text: String): List<LocationHeuristicResult> {
        data class Fragment(val line: Int, val match: MatchResult)
        val lines = text.take(20_000).replace("\r\n", "\n").replace('\r', '\n').lines().take(300)
        val streets = lines.flatMapIndexed { index, line -> streetAddress.findAll(line).map { Fragment(index, it) }.toList() }.take(80)
        val postals = lines.flatMapIndexed { index, line -> postalCity.findAll(line).map { Fragment(index, it) }.toList() }.take(80)
        val usedStreets = mutableSetOf<Fragment>()
        val usedPostals = mutableSetOf<Fragment>()
        val found = mutableListOf<LocationHeuristicResult>()
        val pairs = streets.flatMap { street -> postals.mapNotNull { postal ->
            val distance = kotlin.math.abs(street.line - postal.line)
            val sameLine = distance == 0 && kotlin.math.abs(street.match.range.first - postal.match.range.first) <= 150
            val adjacent = distance == 1 && !distinctAddressLabel.containsMatchIn(lines[maxOf(street.line, postal.line)]) &&
                streets.count { it.line == street.line || it.line == postal.line } == 1 &&
                postals.count { it.line == street.line || it.line == postal.line } == 1
            if (sameLine || adjacent) Triple(street, postal, distance) else null
        } }.sortedWith(compareBy<Triple<Fragment, Fragment, Int>> { it.third }.thenBy {
            kotlin.math.abs(it.first.match.range.first - it.second.match.range.first)
        })
        for ((street, postal, distance) in pairs) {
            if (street in usedStreets || postal in usedPostals) continue
            usedStreets += street
            usedPostals += postal
            val ordered = listOf(street, postal).sortedWith(compareBy<Fragment> { it.line }.thenBy { it.match.range.first })
            found += LocationHeuristicResult(ordered.joinToString(", ") { it.match.value.trim() },
                if (distance == 0) 0.91 else 0.86,
                lines.subList(minOf(street.line, postal.line), maxOf(street.line, postal.line) + 1).joinToString("\n").take(240))
        }
        postals.filterNot { it in usedPostals }.forEach { found += LocationHeuristicResult(it.match.value.trim(), 0.78, lines[it.line].take(240)) }
        streets.filterNot { it in usedStreets }.forEach { found += LocationHeuristicResult(it.match.value.trim(), 0.72, lines[it.line].take(240)) }
        // City hints are useful only on lines without a stronger address, avoiding nested duplicates.
        lines.forEachIndexed { index, line ->
            if (streets.any { it.line == index } || postals.any { it.line == index }) return@forEachIndexed
            val explicit = explicitCity.matchEntire(line)?.groupValues?.get(1)?.trim()
            val city = explicit ?: cityPhrase.findAll(line).map { it.groupValues[1].trim().trimEnd('.', ',', ';') }
                .firstOrNull { it.lowercase() !in CITY_STOP_WORDS }
            if (city != null) found += LocationHeuristicResult(city, if (explicit != null) 0.68 else 0.55, line.take(240))
        }
        fun normalized(value: String) = value.lowercase().replace(Regex("[,\\s]+"), " ").trim()
        val complete = found.filter { it.confidence >= 0.85 }.map { normalized(it.value) }
        return found.sortedByDescending { it.confidence }
            .filter { candidate -> candidate.confidence >= 0.85 || complete.none { address ->
                Regex("(?:^| )" + Regex.escape(normalized(candidate.value)) + "(?:$| )").containsMatchIn(address)
            } }
            .distinctBy { normalized(it.value) }
    }

    private companion object {
        val CITY_STOP_WORDS = setOf("der", "den", "dem", "einem", "einer", "diesem", "dieser", "the")
    }
}
