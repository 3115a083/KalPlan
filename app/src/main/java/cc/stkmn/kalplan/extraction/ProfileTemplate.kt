package cc.stkmn.kalplan.extraction

object ProfileTemplate {
    /** Single pass: field contents cannot introduce new substitutions. */
    fun render(template: String, fields: Map<String, String>): String =
        Regex("\\{feld\\.([^{}]+)\\}").replace(template) { fields[it.groupValues[1]].orEmpty() }.take(100_000)
}
