package cc.stkmn.kalplan.infrastructure.mail

import cc.stkmn.kalplan.domain.port.MailAttachmentMeta
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.ContentType
import java.io.InputStreamReader
import java.nio.charset.Charset

internal data class ExtractedMimeContent(val plainText: String?, val htmlText: String?, val attachments: List<MailAttachmentMeta>)

/** Bound decoded reads, MIME traversal and recursion before allocation. No remote HTML rendering. */
internal class MimeLimitException(message: String) : IllegalArgumentException(message)

internal class MimeTextExtractor(private val maxTextChars: Int = 512_000, private val maxParts: Int = 200, private val maxDepth: Int = 16) {
    init { require(maxTextChars in 1..2_000_000); require(maxParts in 1..1000); require(maxDepth in 1..32) }
    fun extract(part: Part): ExtractedMimeContent {
        val state = State()
        visit(part, state, 0, "")
        return ExtractedMimeContent(state.plain.toString().takeIf { it.isNotBlank() }, state.html.toString().takeIf { it.isNotBlank() }, state.attachments)
    }
    private fun visit(part: Part, state: State, depth: Int, path: String) {
        if (depth > maxDepth || ++state.parts > maxParts) throw MimeLimitException("MIME complexity limit")
        val fileName = part.fileName?.take(512)
        val disposition = part.disposition
        val mimeType = part.contentType.substringBefore(';').trim().take(128)
        val contentId = part.getHeader("Content-ID")?.firstOrNull()?.take(512)?.trim()?.removeSurrounding("<", ">")
        val inline = disposition.equals(Part.INLINE, true) || contentId != null
        if (disposition.equals(Part.ATTACHMENT, true) || !fileName.isNullOrBlank() || inline && mimeType.startsWith("image/", true)) {
            state.attachments += MailAttachmentMeta(fileName, mimeType, part.size.takeIf { it >= 0 }, disposition, contentId, inline, path)
            return
        }
        when {
            part.isMimeType("text/plain") || part.isMimeType("text/html") -> {
                val target = if (part.isMimeType("text/plain")) state.plain else state.html
                val charsetName = runCatching { ContentType(part.contentType).getParameter("charset") }.getOrNull() ?: "UTF-8"
                val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
                InputStreamReader(part.inputStream, charset).use { reader ->
                    if (target.isNotEmpty()) target.append('\n')
                    val buffer = CharArray(4096)
                    while (true) {
                        val remaining = maxTextChars - state.plain.length - state.html.length
                        // Fail closed on truncation: partial dates must not become apparently safe requests.
                        if (remaining <= 0) { if (reader.read() != -1) throw MimeLimitException("MIME text limit"); break }
                        val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                        if (count < 0) break
                        target.append(buffer, 0, count)
                    }
                }
            }
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as? Multipart ?: return
                if (multipart.count > maxParts - state.parts) throw MimeLimitException("MIME part limit")
                for (i in 0 until multipart.count) visit(multipart.getBodyPart(i), state, depth + 1, if (path.isBlank()) i.toString() else "$path/$i")
            }
            // Forwarded messages are attachments. Never extract a historical inner appointment as current.
            part.isMimeType("message/rfc822") -> state.attachments.add(MailAttachmentMeta(fileName ?: "forwarded.eml", mimeType, part.size.takeIf { it >= 0 }, disposition, contentId, inline, path))
        }
    }
    private class State {
        val plain = StringBuilder()
        val html = StringBuilder()
        val attachments = mutableListOf<MailAttachmentMeta>()
        var parts = 0
    }
}
