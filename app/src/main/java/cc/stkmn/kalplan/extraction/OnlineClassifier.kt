package cc.stkmn.kalplan.extraction

import java.util.Locale

enum class MeetingMode {
    ONLINE,
    ONSITE,
    HYBRID,
    UNKNOWN
}

data class MeetingModeResult(
    val mode: MeetingMode,
    val confidence: Double,
    val evidence: String?
)

class OnlineClassifier {
    private val onlineTerms = listOf(
        "online",
        "remote",
        "videokonferenz",
        "video call",
        "teams",
        "zoom",
        "google meet",
        "webex"
    )

    private val onsiteTerms = listOf(
        "vor ort",
        "vor-ort",
        "präsenz",
        "praesenz",
        "onsite",
        "on site"
    )

    private val meetingLinkPatterns = listOf(
        Regex("https?://[^\\s]*zoom\\.us/", RegexOption.IGNORE_CASE),
        Regex("https?://teams\\.microsoft\\.com/", RegexOption.IGNORE_CASE),
        Regex("https?://meet\\.google\\.com/", RegexOption.IGNORE_CASE),
        Regex("https?://[^\\s]*webex\\.com/", RegexOption.IGNORE_CASE)
    )

    fun classify(
        structuredValue: String?,
        body: String
    ): MeetingModeResult {
        structuredValue
            ?.takeIf { it.isNotBlank() }
            ?.let { value ->
                classifyText(value, structured = true)?.let { return it }
            }

        meetingLinkPatterns.firstOrNull { it.containsMatchIn(body) }?.let {
            return MeetingModeResult(MeetingMode.ONLINE, 0.97, it.pattern)
        }

        return classifyText(body, structured = false)
            ?: MeetingModeResult(MeetingMode.UNKNOWN, 0.0, null)
    }

    private fun classifyText(
        value: String,
        structured: Boolean
    ): MeetingModeResult? {
        val lower = value.lowercase(Locale.ROOT)
        val online = onlineTerms.firstOrNull { lower.contains(it) }
        val onsite = onsiteTerms.firstOrNull { lower.contains(it) }

        return when {
            online != null && onsite != null ->
                MeetingModeResult(
                    MeetingMode.HYBRID,
                    if (structured) 0.98 else 0.72,
                    online + " + " + onsite
                )
            online != null ->
                MeetingModeResult(
                    MeetingMode.ONLINE,
                    if (structured) 0.99 else 0.84,
                    online
                )
            onsite != null ->
                MeetingModeResult(
                    MeetingMode.ONSITE,
                    if (structured) 0.99 else 0.84,
                    onsite
                )
            else -> null
        }
    }
}
