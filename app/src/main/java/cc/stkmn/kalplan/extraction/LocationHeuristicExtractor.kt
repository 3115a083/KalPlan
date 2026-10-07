package cc.stkmn.kalplan.extraction

data class LocationHeuristicResult(
    val value: String,
    val confidence: Double,
    val evidence: String
)

class LocationHeuristicExtractor {
    private val streetAddress = Regex(
        "(?i)(?:\\b(?:am|an der|auf der|unter den|zum|zur)\\s+)?" +
            "[\\p{L}][\\p{L}.'’/-]*(?:straße|strasse|str\\.?|weg|allee|platz|gasse|ring|ufer|chaussee|damm|steig|stieg|pfad|promenade)" +
            "\\s+\\d{1,5}[a-zA-Z]?(?:\\s*[-/]\\s*\\d{1,5}[a-zA-Z]?)?"
    )
    private val postalCity = Regex(
        "(?<!\\d)\\d{5}\\s+[\\p{L}][\\p{L} .'-]{1,50}(?!\\d)"
    )
    private val cityPhrase = Regex(
        "(?i)\\b(?:in|bei|at)\\s+([A-ZÄÖÜ][\\p{L}.'-]{2,}(?:\\s+[A-ZÄÖÜ][\\p{L}.'-]{2,}){0,2})"
    )

    fun extract(text: String): LocationHeuristicResult? {
        val street = streetAddress.find(text)
        val postal = postalCity.find(text)

        if (street != null && postal != null) {
            val value = if (street.range.first <= postal.range.first) {
                street.value.trim() + ", " + postal.value.trim()
            } else {
                postal.value.trim() + ", " + street.value.trim()
            }
            return LocationHeuristicResult(
                value = value,
                confidence = 0.91,
                evidence = street.value + " | " + postal.value
            )
        }

        if (postal != null) {
            return LocationHeuristicResult(
                value = postal.value.trim(),
                confidence = 0.86,
                evidence = postal.value
            )
        }

        if (street != null) {
            return LocationHeuristicResult(
                value = street.value.trim(),
                confidence = 0.80,
                evidence = street.value
            )
        }

        val city = cityPhrase.findAll(text)
            .map { it.groupValues[1].trim() }
            .firstOrNull { candidate ->
                candidate.lowercase() !in CITY_STOP_WORDS
            }

        return city?.let {
            LocationHeuristicResult(
                value = it,
                confidence = 0.58,
                evidence = it
            )
        }
    }

    private companion object {
        val CITY_STOP_WORDS = setOf(
            "der",
            "den",
            "dem",
            "einem",
            "einer",
            "diesem",
            "dieser",
            "the"
        )
    }
}
