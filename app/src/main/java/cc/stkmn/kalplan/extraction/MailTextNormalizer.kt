package cc.stkmn.kalplan.extraction

class MailTextNormalizer {
    fun htmlToText(html: String): String = org.jsoup.Jsoup.parse(html.take(512_000)).apply {
        select("script,style,iframe,object").remove()
        select("br").append("\n")
        select("p,div,tr,li").prepend("\n")
    }.wholeText()

    fun normalize(input: ExtractionInput): ExtractionInput = input.copy(
        subject = normalizeText(input.subject, preserveNewlines = false),
        body = normalizeText(input.body, preserveNewlines = true)
    )

    fun normalizeText(
        value: String,
        preserveNewlines: Boolean = true
    ): String {
        var text = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2007', ' ')
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .replace("\uFFFC", "")

        text = if (preserveNewlines) {
            text.lineSequence()
                .joinToString("\n") { line ->
                    line.replace(Regex("[\\p{Zs}\\t]+"), " ").trimEnd()
                }
                .replace(Regex("\n{4,}"), "\n\n\n")
                .trim()
        } else {
            text.replace(Regex("[\\s\\p{Z}]+"), " ").trim()
        }

        return text
    }
}

