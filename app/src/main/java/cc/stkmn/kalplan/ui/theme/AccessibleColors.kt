package cc.stkmn.kalplan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Opaque surfaces only. Keep the chosen hue when it meets normal-text contrast. */
fun readableContentColor(preferred: Color, background: Color): Color {
    fun contrast(foreground: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
    if (contrast(preferred) >= 4.5f) return preferred
    return if (contrast(Color.Black) >= contrast(Color.White)) Color.Black else Color.White
}
