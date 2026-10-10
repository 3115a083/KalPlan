package cc.stkmn.kalplan.infrastructure.reply

import cc.stkmn.kalplan.data.Settings
import cc.stkmn.kalplan.data.StoredRequest
import jakarta.mail.internet.InternetAddress

object ReplyPolicy {
    fun address(value: String): String {
        require(value.length in 3..320 && !value.any { it == '\r' || it == '\n' }) { "Invalid recipient" }
        val parsed = InternetAddress.parse(value, true)
        require(parsed.size == 1 && !parsed[0].isGroup) { "Exactly one recipient required" }
        parsed[0].validate()
        return parsed[0].address
    }
    fun recipient(request: StoredRequest, settings: Settings): String =
        address(if (settings.debug) settings.debugTestAddress else request.recipient)
    fun canSend(request: StoredRequest): Boolean = request.pending && !request.demo && request.accountId.isNotBlank()
    fun header(value: String): String {
        require(value.length <= 998 && !value.contains('\r') && !value.contains('\n')) { "Invalid mail header" }
        return value
    }
    fun messageId(value: String?): String? = value?.takeIf {
        it.length <= 998 && it.matches(Regex("<[^<>\\s]+@[^<>\\s]+>"))
    }
}
