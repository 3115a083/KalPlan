package cc.stkmn.kalplan.extraction

import com.google.re2j.Pattern

/** Linear-time profile regex. Unsupported backreferences/lookarounds fail validation. */
object SafePattern {
    fun compile(value: String, ignoreCase: Boolean = false): Pattern {
        require(value.length in 1..4096) { "Regex length limit" }
        return Pattern.compile(value, Pattern.MULTILINE or if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)
    }
}
