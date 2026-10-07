package cc.stkmn.kalplan.data

import cc.stkmn.kalplan.extraction.ExtractionProfile
import kotlinx.serialization.Serializable

@Serializable
data class MailAccount(
    val id: String,
    val name: String,
    val username: String,
    val address: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val imapStartTls: Boolean = false,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val smtpStartTls: Boolean = false,
    val folders: List<String> = listOf("INBOX"),
    val signature: String = "",
    val enabled: Boolean = true,
    val folderProfiles: Map<String, String> = emptyMap(),
    val authMode: String = "PASSWORD",
    val oauthClientId: String = "",
    val oauthAuthorizationEndpoint: String = "",
    val oauthTokenEndpoint: String = "",
    val oauthScope: String = ""
)

@Serializable
data class CalendarPrivacy(
    val id: String,
    val included: Boolean = false,
    val showTitle: Boolean = false,
    val showLocation: Boolean = false,
    val showDescription: Boolean = false,
    val useHiddenLocationForRouting: Boolean = false
)

@Serializable
data class LabelPolicy(
    val name: String,
    val keywords: List<String> = emptyList(),
    val senderContains: String = "",
    val score: Int = 0,
    val durationMinutes: Int? = null,
    val durationPriority: Int = 0,
    val shortThresholdMinutes: Int? = null,
    val shortScore: Int = 0,
    val requiredLabels: Set<String> = emptySet(),
    val excludedLabels: Set<String> = emptySet()
)

@Serializable
data class ValueSettings(
    val enabled: Boolean = false,
    val workCentsPerHour: Long = 6000,
    val billingStepMinutes: Int = 15,
    val roundUp: Boolean = true,
    val travelCentsPerHour: Long = 0,
    val centsPerKm: Long = 42,
    val flatCents: Long = 0,
    val roundTrip: Boolean = false,
    val distanceBands: List<DistanceBand> = emptyList()
)
@Serializable
data class DistanceBand(val upToKm: Double, val cents: Long)

@Serializable
data class Settings(
    val syncMinutes: Int = 15,
    val pauseWeekends: Boolean = false,
    val pauseFrom: String = "",
    val pauseUntil: String = "",
    val pausedWeekdays: Set<Int> = emptySet(),
    val defaultDuration: Int = 60,
    val staleHours: Int = 72,
    val beforeBuffer: Int = 20,
    val afterBuffer: Int = 20,
    val originThreshold: Int = 120,
    val originName: String = "",
    val originAddress: String = "",
    val theme: String = "KALPLAN",
    val primaryHex: String = "",
    val acceptTemplate: String = "Guten Tag,\n\nich kann den Termin {date} um {time} übernehmen. Bitte bestätigen Sie den Auftrag.\n\nMit freundlichen Grüßen",
    val declineTemplate: String = "Guten Tag,\n\nleider kann ich diesen Auftrag nicht übernehmen.\n\nMit freundlichen Grüßen",
    val reservationCalendarId: String = "",
    val reservationDescription: String = "NONE",
    val reservationTemplate: String = "KalPlan-ID: {id}\n{subject}\n{date} {time}",
    val reservationExcludeBlocks: List<String> = emptyList(),
    val reservationExcerpts: List<String> = emptyList(),
    val attachments: String = "RELEVANT",
    val attachmentRules: List<AttachmentRule> = emptyList(),
    val routingProvider: String = "GOOGLE_MAPS",
    val routingDailyLimit: Int = 10,
    val debug: Boolean = false,
    val debugTestAddress: String = "",
    val debugSendToTest: Boolean = false,
    val value: ValueSettings = ValueSettings(),
    val labels: List<LabelPolicy> = listOf(
        LabelPolicy("Medizin", listOf("medizin", "arzt", "praxis"), score = 30),
        LabelPolicy("LWL", listOf("lwl"), score = -8, shortThresholdMinutes = 120, shortScore = -35),
        LabelPolicy("Beratung", listOf("beratung", "consultation"), score = 10)
    )
)

@Serializable
data class StoredCandidate(
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val durationMinutes: Int = 60,
    val assumed: Boolean = false,
    val durationSource: String = "EXTRACTED",
    val location: String = "",
    val mode: String = "UNKNOWN",
    val confidence: Double = 0.0,
    val warnings: List<String> = emptyList(),
    val relation: String = "SINGLE"
)
@Serializable
data class StoredRequest(
    val id: String,
    val accountId: String = "",
    val folder: String = "",
    val messageId: String? = null,
    val sourceStableId: String? = null,
    val sender: String,
    val recipient: String = sender,
    val subject: String,
    val body: String,
    val receivedMillis: Long,
    val labels: List<String> = emptyList(),
    val candidates: List<StoredCandidate> = emptyList(),
    val selectedCandidate: Int? = null,
    val issues: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val status: String = "NEW",
    val unclear: Boolean = true,
    val manual: Boolean = false,
    val demo: Boolean = false,
    val attachmentMeta: List<StoredAttachment> = emptyList(),
    val reservationEventId: String? = null,
    val replyId: String? = null,
    val failureCode: String? = null,
    val travelMinutes: Int? = null,
    val routeOrigin: String = "",
    val routeDestination: String = "",
    val routeAfterOrigin: String = "",
    val routeAfterDestination: String = "",
    val distanceKm: Double? = null,
    val routeCheckedMillis: Long? = null,
    val travelAfterMinutes: Int? = null,
    val travelAfterCheckedMillis: Long? = null,
    val manualOrigin: String = "",
    val manualAfterDestination: String = ""
) {
    val pending: Boolean get() = status in setOf("NEW", "LATER", "UNCLEAR")
    val candidate: StoredCandidate? get() = selectedCandidate?.let { candidates.getOrNull(it) }
}
@Serializable
data class StoredAttachment(val name: String, val mime: String, val size: Int?, val inline: Boolean, val partPath: String = "")
@Serializable
data class Budget(val date: String, val used: Int)
@Serializable
data class AppData(
    val schema: Int = 1,
    val settings: Settings = Settings(),
    val accounts: List<MailAccount> = emptyList(),
    val calendars: List<CalendarPrivacy> = emptyList(),
    val profiles: List<ExtractionProfile> = emptyList(),
    val requests: List<StoredRequest> = emptyList(),
    val cursors: Map<String, String> = emptyMap(),
    val routeBudgets: Map<String, Budget> = emptyMap(),
    val lastSyncMillis: Long? = null,
    val diagnostics: List<String> = emptyList()
)
