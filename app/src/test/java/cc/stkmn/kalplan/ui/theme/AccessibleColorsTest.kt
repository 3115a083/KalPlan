package cc.stkmn.kalplan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AccessibleColorsTest {
    @Test fun chosenLabelAndStatusColorsRemainReadableOnBothSurfaces() {
        val foregrounds = listOf(Color(0xFFFFFF00), Color(0xFF000000), Color(0xFFFFFFFF),
            Color(0xFF6750A4), Color(0xFF7AE4AF), Color(0xFF12653B), Color(0xFFFF9CB2),
            Color(0xFFAC2445), Color(0xFFFFD17D), Color(0xFF835500))
        for (surface in listOf(Color.White, Color(0xFF111117))) {
            for (preferred in foregrounds) {
                val background = preferred.copy(alpha = 0.14f).compositeOver(surface)
                val content = readableContentColor(preferred, background)
                val contrast = (maxOf(content.luminance(), background.luminance()) + 0.05f) /
                    (minOf(content.luminance(), background.luminance()) + 0.05f)
                assertTrue("Contrast $contrast for $preferred on $surface", contrast >= 4.5f)
            }
        }
    }

    @Test fun midToneCustomPrimaryUsesBlackText() {
        assertEquals(Color.Black, readableContentColor(Color.White, Color(0xFF777777)))
    }

    @Test fun readablePreferredHueIsPreserved() {
        val preferred = Color(0xFF12653B)
        assertEquals(preferred, readableContentColor(preferred, Color.White))
    }
}
