package cc.stkmn.kalplan.extraction

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

enum class TemporalLocale {
    DE_DE,
    EN_GB,
    EN_US
}

enum class DateRelation {
    SINGLE,
    ALTERNATIVE,
    MULTIPLE_OPTIONS,
    MULTIPLE_UNSPECIFIED
}

data class ParsedTemporalCandidate(
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val start: ZonedDateTime?,
    val end: ZonedDateTime?,
    val relation: DateRelation,
    val explicitYear: Boolean,
    val confidence: Double,
    val warnings: List<String>
)

data class TemporalParseResult(
    val candidates: List<ParsedTemporalCandidate>,
    val durationMinutes: Int?,
    val issues: List<ExtractionIssue>
)

class FlexibleTemporalParser {
    private enum class DateOrder { DMY, MDY }

    private data class Rules(
        val locale: Locale,
        val order: DateOrder,
        val germanWords: Boolean,
        val englishWords: Boolean
    )

    private data class DateToken(
        val date: LocalDate,
        val range: IntRange,
        val raw: String,
        val explicitYear: Boolean,
        val suspiciousYear: Boolean
    )

    private val isoDate = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val numericDate = Regex(
        "(?<!\\d)(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\s*[./-]\\s*(\\d{2,4}))?\\.?(?!\\d)"
    )
    private val germanNamedDate = Regex(
        "(?<!\\d)(\\d{1,2})\\.?\\s+(januar|jan|februar|feb|märz|maerz|mrz|april|apr|mai|juni|jun|juli|jul|august|aug|september|sep|sept|oktober|okt|november|nov|dezember|dez)(?:\\s+(\\d{2,4}))?",
        RegexOption.IGNORE_CASE
    )
    private val englishDmyDate = Regex(
        "(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|sept|october|oct|november|nov|december|dec)(?:,?\\s+(\\d{2,4}))?",
        RegexOption.IGNORE_CASE
    )
    private val englishMdyDate = Regex(
        "\\b(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|sept|october|oct|november|nov|december|dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{2,4}))?",
        RegexOption.IGNORE_CASE
    )

    private val timeRange = Regex(
        "(?<!\\d)(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(am|pm|uhr)?\\s*(?:-|–|—|bis|to)\\s*(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(am|pm|uhr)?(?!\\d)",
        RegexOption.IGNORE_CASE
    )
    private val explicitSingleTime = Regex(
        "(?<!\\d)(\\d{1,2})(?:[:.]([0-5]\\d))\\s*(am|pm|uhr)?(?!\\d)",
        RegexOption.IGNORE_CASE
    )
    private val markedSingleTime = Regex(
        "(?<!\\d)(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(am|pm|uhr)(?!\\d)",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        dateText: String,
        timeText: String = dateText,
        durationText: String = "",
        locale: TemporalLocale = TemporalLocale.DE_DE,
        reference: ZonedDateTime
    ): TemporalParseResult {
        val rules = rules(locale)
        val issues = mutableListOf<ExtractionIssue>()
        val dates = findDates(dateText, reference.toLocalDate(), rules, issues)
        val relation = relationFor(dateText, dates)

        if (dates.any { it.suspiciousYear }) {
            issues += ExtractionIssue(
                code = "explicit_year_suspicious",
                message = "An explicit year looks implausible for a new request and must be checked.",
                severity = IssueSeverity.NEEDS_REVIEW
            )
        }

        val sanitizedTimeText = if (timeText === dateText || timeText == dateText) {
            removeRanges(timeText, dates.map { it.range })
        } else {
            timeText
        }

        val times = parseTimes(sanitizedTimeText, rules)
        val duration = parseDurationMinutes(durationText)
        if (durationText.isNotBlank() && duration == null) {
            issues += ExtractionIssue(
                code = "duration_unparsed",
                message = "Duration could not be parsed safely.",
                severity = IssueSeverity.WARNING
            )
        }

        if (dates.isEmpty()) {
            issues += ExtractionIssue(
                code = "date_missing",
                message = "No date could be extracted.",
                severity = IssueSeverity.NEEDS_REVIEW
            )
            return TemporalParseResult(emptyList(), duration, issues)
        }

        if (times.first == null) {
            issues += ExtractionIssue(
                code = "time_missing",
                message = "No start time could be extracted.",
                severity = IssueSeverity.NEEDS_REVIEW
            )
        }

        val candidates = dates.map { token ->
            val warnings = mutableListOf<String>()
            var confidence = if (token.explicitYear) 0.96 else 0.88

            if (!token.explicitYear) {
                warnings += "year_inferred"
            }
            if (token.suspiciousYear) {
                warnings += "explicit_year_suspicious"
                confidence = minOf(confidence, 0.58)
            }
            if (relation != DateRelation.SINGLE) {
                warnings += when (relation) {
                    DateRelation.ALTERNATIVE -> "alternative_date"
                    DateRelation.MULTIPLE_OPTIONS -> "multiple_dates"
                    DateRelation.MULTIPLE_UNSPECIFIED -> "multiple_dates_relation_unclear"
                    DateRelation.SINGLE -> ""
                }
            }

            val start = times.first?.let { LocalDateTime.of(token.date, it).atZone(reference.zone) }
            val end = when {
                start == null -> null
                times.second != null -> {
                    var endDate = token.date
                    if (times.second.isBefore(times.first)) endDate = endDate.plusDays(1)
                    LocalDateTime.of(endDate, times.second).atZone(reference.zone)
                }
                duration != null -> start.plusMinutes(duration.toLong())
                else -> null
            }

            ParsedTemporalCandidate(
                date = token.date,
                startTime = times.first,
                endTime = times.second ?: end?.toLocalTime(),
                start = start,
                end = end,
                relation = relation,
                explicitYear = token.explicitYear,
                confidence = confidence,
                warnings = warnings.filter { it.isNotEmpty() }
            )
        }

        return TemporalParseResult(candidates, duration, issues)
    }

    fun parseDurationMinutes(value: String): Int? {
        val normalized = value.trim().lowercase(Locale.ROOT).replace(',', '.')
        if (normalized.isBlank()) return null
        if (normalized in setOf("eine stunde", "eine std", "one hour", "1 stunde", "1 hour")) return 60
        if (normalized in setOf("anderthalb stunden", "anderthalb stunde")) return 90

        var total = 0.0
        var matched = false

        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:h|std\\.?|stunden?|hours?)", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .forEach {
                total += (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60.0
                matched = true
            }

        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:min\\.?|minuten?|minutes?)", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .forEach {
                total += it.groupValues[1].toDoubleOrNull() ?: 0.0
                matched = true
            }

        if (matched && total > 0.0) return total.roundToInt()

        val plainHours = normalized.toDoubleOrNull()
        return plainHours
            ?.takeIf { it > 0.0 && it <= 72.0 }
            ?.let { (it * 60.0).roundToInt() }
    }

    private fun findDates(
        text: String,
        referenceDate: LocalDate,
        rules: Rules,
        issues: MutableList<ExtractionIssue>
    ): List<DateToken> {
        val iso = isoDate.findAll(text).mapNotNull { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            buildToken(year, month, day, match.range, match.value, true, referenceDate)
        }.toList()
        if (iso.isNotEmpty()) return iso.distinctBy { it.date to it.range }

        val numericMatches = numericDate.findAll(text).toList()
        if (numericMatches.isNotEmpty()) {
            val explicitYears = numericMatches.mapNotNull {
                it.groupValues[3].takeIf(String::isNotBlank)?.let { y -> resolveExplicitYear(y) }
            }
            val sharedYear = explicitYears.distinct().singleOrNull()

            val parsed = numericMatches.mapNotNull { match ->
                val first = match.groupValues[1].toInt()
                val second = match.groupValues[2].toInt()
                val yearText = match.groupValues[3]
                val dayMonth = when (rules.order) {
                    DateOrder.DMY -> first to second
                    DateOrder.MDY -> second to first
                }

                val explicit = yearText.isNotBlank()
                val year = when {
                    explicit -> resolveExplicitYear(yearText)
                    sharedYear != null -> sharedYear
                    else -> inferYear(dayMonth.second, dayMonth.first, referenceDate)
                }

                buildToken(
                    year = year,
                    month = dayMonth.second,
                    day = dayMonth.first,
                    range = match.range,
                    raw = match.value,
                    explicitYear = explicit || sharedYear != null,
                    referenceDate = referenceDate
                )
            }

            if (parsed.size < numericMatches.size) {
                issues += ExtractionIssue(
                    code = "invalid_date",
                    message = "At least one numeric date was invalid.",
                    severity = IssueSeverity.WARNING
                )
            }
            if (parsed.isNotEmpty()) return parsed.distinctBy { it.date to it.range }
        }

        if (rules.germanWords) {
            val named = germanNamedDate.findAll(text).mapNotNull { match ->
                val day = match.groupValues[1].toInt()
                val month = germanMonth(match.groupValues[2]) ?: return@mapNotNull null
                val yearText = match.groupValues[3]
                val explicit = yearText.isNotBlank()
                val year = if (explicit) resolveExplicitYear(yearText)
                else inferYear(month, day, referenceDate)
                buildToken(year, month, day, match.range, match.value, explicit, referenceDate)
            }.toList()
            if (named.isNotEmpty()) return named.distinctBy { it.date to it.range }
        }

        if (rules.englishWords) {
            val dmy = englishDmyDate.findAll(text).mapNotNull { match ->
                val day = match.groupValues[1].toInt()
                val month = englishMonth(match.groupValues[2]) ?: return@mapNotNull null
                val yearText = match.groupValues[3]
                val explicit = yearText.isNotBlank()
                val year = if (explicit) resolveExplicitYear(yearText)
                else inferYear(month, day, referenceDate)
                buildToken(year, month, day, match.range, match.value, explicit, referenceDate)
            }.toList()
            if (dmy.isNotEmpty()) return dmy.distinctBy { it.date to it.range }

            val mdy = englishMdyDate.findAll(text).mapNotNull { match ->
                val month = englishMonth(match.groupValues[1]) ?: return@mapNotNull null
                val day = match.groupValues[2].toInt()
                val yearText = match.groupValues[3]
                val explicit = yearText.isNotBlank()
                val year = if (explicit) resolveExplicitYear(yearText)
                else inferYear(month, day, referenceDate)
                buildToken(year, month, day, match.range, match.value, explicit, referenceDate)
            }.toList()
            if (mdy.isNotEmpty()) return mdy.distinctBy { it.date to it.range }
        }

        return emptyList()
    }

    private fun buildToken(
        year: Int,
        month: Int,
        day: Int,
        range: IntRange,
        raw: String,
        explicitYear: Boolean,
        referenceDate: LocalDate
    ): DateToken? {
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val dayDelta = ChronoUnit.DAYS.between(referenceDate, date)
        val suspicious = explicitYear && (dayDelta < -180 || dayDelta > 1095)
        return DateToken(date, range, raw, explicitYear, suspicious)
    }

    private fun inferYear(month: Int, day: Int, referenceDate: LocalDate): Int {
        val sameYear = runCatching { LocalDate.of(referenceDate.year, month, day) }.getOrNull()
            ?: return referenceDate.year
        return if (sameYear.isBefore(referenceDate.minusDays(60))) referenceDate.year + 1
        else referenceDate.year
    }

    private fun resolveExplicitYear(text: String): Int {
        val value = text.toInt()
        return if (text.length <= 2) {
            if (value <= 69) 2000 + value else 1900 + value
        } else {
            value
        }
    }

    private fun relationFor(text: String, dates: List<DateToken>): DateRelation {
        if (dates.size <= 1) return DateRelation.SINGLE
        val lower = text.lowercase(Locale.ROOT)
        val between = lower.substring(
            dates.minOf { it.range.first }.coerceAtLeast(0),
            (dates.maxOf { it.range.last } + 1).coerceAtMost(lower.length)
        )

        return when {
            Regex("\\b(oder|or|alternativ|either)\\b").containsMatchIn(between) ->
                DateRelation.ALTERNATIVE
            Regex("\\b(und|and|sowie)\\b|&").containsMatchIn(between) ->
                DateRelation.MULTIPLE_OPTIONS
            else ->
                DateRelation.MULTIPLE_UNSPECIFIED
        }
    }

    private fun parseTimes(text: String, rules: Rules): Pair<LocalTime?, LocalTime?> {
        val range = timeRange.find(text)
        if (range != null) {
            val first = parseTime(
                range.groupValues[1],
                range.groupValues[2],
                range.groupValues[3],
                rules
            )
            val second = parseTime(
                range.groupValues[4],
                range.groupValues[5],
                range.groupValues[6],
                rules
            )
            if (first != null && second != null) return first to second
        }

        val single = explicitSingleTime.find(text) ?: markedSingleTime.find(text)
        if (single != null) {
            return parseTime(
                single.groupValues[1],
                single.groupValues[2],
                single.groupValues[3],
                rules
            ) to null
        }

        val trimmed = text.trim()
        if (Regex("^\\d{1,2}$").matches(trimmed)) {
            return trimmed.toIntOrNull()
                ?.takeIf { it in 0..23 }
                ?.let { LocalTime.of(it, 0) } to null
        }

        return null to null
    }

    private fun parseTime(
        hourText: String,
        minuteText: String,
        markerText: String,
        rules: Rules
    ): LocalTime? {
        var hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.ifBlank { "0" }.toIntOrNull() ?: return null
        val marker = markerText.lowercase(rules.locale)

        if (marker == "am" || marker == "pm") {
            if (hour !in 1..12) return null
            if (marker == "am" && hour == 12) hour = 0
            if (marker == "pm" && hour != 12) hour += 12
        } else if (hour !in 0..23) {
            return null
        }

        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun removeRanges(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val chars = text.toCharArray()
        for (range in ranges) {
            for (index in range) {
                if (index in chars.indices) chars[index] = ' '
            }
        }
        return chars.concatToString()
    }

    private fun rules(locale: TemporalLocale): Rules = when (locale) {
        TemporalLocale.DE_DE -> Rules(Locale.GERMANY, DateOrder.DMY, true, false)
        TemporalLocale.EN_GB -> Rules(Locale.UK, DateOrder.DMY, false, true)
        TemporalLocale.EN_US -> Rules(Locale.US, DateOrder.MDY, false, true)
    }

    private fun germanMonth(value: String): Int? = when (
        value.lowercase(Locale.GERMANY)
            .replace("ä", "ae")
    ) {
        "januar", "jan" -> 1
        "februar", "feb" -> 2
        "maerz", "mrz" -> 3
        "april", "apr" -> 4
        "mai" -> 5
        "juni", "jun" -> 6
        "juli", "jul" -> 7
        "august", "aug" -> 8
        "september", "sep", "sept" -> 9
        "oktober", "okt" -> 10
        "november", "nov" -> 11
        "dezember", "dez" -> 12
        else -> null
    }

    private fun englishMonth(value: String): Int? = when (value.lowercase(Locale.US)) {
        "january", "jan" -> 1
        "february", "feb" -> 2
        "march", "mar" -> 3
        "april", "apr" -> 4
        "may" -> 5
        "june", "jun" -> 6
        "july", "jul" -> 7
        "august", "aug" -> 8
        "september", "sep", "sept" -> 9
        "october", "oct" -> 10
        "november", "nov" -> 11
        "december", "dec" -> 12
        else -> null
    }
}
