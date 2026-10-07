package cc.stkmn.kalplan.data

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentRule(
    val show: Boolean = false,
    val mimePrefix: String = "",
    val extension: String = "",
    val nameContains: String = "",
    val minBytes: Int? = null,
    val maxBytes: Int? = null,
    val inline: Boolean? = null
)

object AttachmentPolicy {
    fun visible(file: StoredAttachment, settings: Settings): Boolean {
        if (settings.attachments == "IGNORE") return false
        if (settings.attachments == "ALL") return true
        val matched = settings.attachmentRules.firstOrNull { rule ->
            (rule.mimePrefix.isBlank() || file.mime.startsWith(rule.mimePrefix, true)) &&
            (rule.extension.isBlank() || file.name.substringAfterLast('.', "").equals(rule.extension.removePrefix("."), true)) &&
            (rule.nameContains.isBlank() || file.name.contains(rule.nameContains, true)) &&
            (rule.inline == null || file.inline == rule.inline) &&
            (rule.minBytes == null || file.size?.let { it >= rule.minBytes } == true) &&
            (rule.maxBytes == null || file.size?.let { it <= rule.maxBytes } == true)
        }
        return matched?.show ?: !(file.inline && file.mime.startsWith("image/", true) && file.size?.let { it < 150_000 } == true)
    }
}
