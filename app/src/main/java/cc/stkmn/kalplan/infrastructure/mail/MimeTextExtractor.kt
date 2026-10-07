package cc.stkmn.kalplan.infrastructure.mail

import cc.stkmn.kalplan.domain.port.MailAttachmentMeta
import jakarta.mail.Multipart
import jakarta.mail.Part

internal data class ExtractedMimeContent(
    val plainText: String?,
    val htmlText: String?,
    val attachments: List<MailAttachmentMeta>
)

internal class MimeTextExtractor(
    private val maxTextChars: Int = 512_000
) {
    fun extract(part: Part): ExtractedMimeContent {
        val state = State(maxTextChars)
        visit(part, state)
        return ExtractedMimeContent(
            plainText = state.plain.takeIf { it.isNotBlank() },
            htmlText = state.html.takeIf { it.isNotBlank() },
            attachments = state.attachments.toList()
        )
    }

    private fun visit(part: Part, state: State) {
        val fileName = runCatching { part.fileName }.getOrNull()
        val disposition = runCatching { part.disposition }.getOrNull()
        val mimeType = runCatching { part.contentType.substringBefore(';').trim() }
            .getOrDefault("application/octet-stream")
        val contentId = runCatching { part.getHeader("Content-ID")?.firstOrNull() }
            .getOrNull()
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")

        val explicitAttachment = disposition.equals(Part.ATTACHMENT, ignoreCase = true)
        val inline = disposition.equals(Part.INLINE, ignoreCase = true) || contentId != null
        val looksLikeFile = !fileName.isNullOrBlank()

        if (explicitAttachment || looksLikeFile || (inline && mimeType.startsWith("image/", true))) {
            state.attachments += MailAttachmentMeta(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = runCatching { part.size }.getOrNull()?.takeIf { it >= 0 },
                disposition = disposition,
                contentId = contentId,
                inline = inline
            )
            return
        }

        when {
            part.isMimeType("text/plain") -> {
                val text = runCatching { part.content as? String }.getOrNull() ?: return
                state.appendPlain(text)
            }

            part.isMimeType("text/html") -> {
                val text = runCatching { part.content as? String }.getOrNull() ?: return
                state.appendHtml(text)
            }

            part.isMimeType("multipart/*") -> {
                val multipart = runCatching { part.content as? Multipart }.getOrNull() ?: return
                for (index in 0 until multipart.count) {
                    visit(multipart.getBodyPart(index), state)
                    if (state.isTextFull()) break
                }
            }

            part.isMimeType("message/rfc822") -> {
                val nested = runCatching { part.content as? Part }.getOrNull() ?: return
                visit(nested, state)
            }
        }
    }

    private class State(
        private val maxChars: Int
    ) {
        val plain = StringBuilder()
        val html = StringBuilder()
        val attachments = mutableListOf<MailAttachmentMeta>()

        fun appendPlain(value: String) = appendBounded(plain, value)

        fun appendHtml(value: String) = appendBounded(html, value)

        fun isTextFull(): Boolean = plain.length + html.length >= maxChars

        private fun appendBounded(target: StringBuilder, value: String) {
            val remaining = (maxChars - plain.length - html.length).coerceAtLeast(0)
            if (remaining == 0) return
            if (target.isNotEmpty()) target.append("\n")
            target.append(value.take(remaining))
        }
    }
}
