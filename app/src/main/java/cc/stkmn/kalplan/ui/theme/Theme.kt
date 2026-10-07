package cc.stkmn.kalplan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeChoice {
    MATERIAL_YOU,
    KALPLAN,
    NEUTRAL_BUSINESS,
    TURQUOISE,
    HIGH_CONTRAST
}

private val KalPlanLight = lightColorScheme(
    primary = Color(0xFF4F52C9),
    secondary = Color(0xFF167B72),
    tertiary = Color(0xFF9A5B00)
)

private val KalPlanDark = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    secondary = Color(0xFF7AD9CE),
    tertiary = Color(0xFFFFB95F)
)

private val NeutralLight = lightColorScheme(
    primary = Color(0xFF4D5D6C),
    secondary = Color(0xFF59636C),
    tertiary = Color(0xFF6B5F55)
)

private val NeutralDark = darkColorScheme(
    primary = Color(0xFFC4D4E3),
    secondary = Color(0xFFC1CBD4),
    tertiary = Color(0xFFD7C5B8)
)

private val TurquoiseLight = lightColorScheme(
    primary = Color(0xFF006B62),
    secondary = Color(0xFF4A635F),
    tertiary = Color(0xFF456179)
)

private val TurquoiseDark = darkColorScheme(
    primary = Color(0xFF54DBCD),
    secondary = Color(0xFFB1CCC7),
    tertiary = Color(0xFFADC9E5)
)

private val HighContrastLight = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF005A9C),
    tertiary = Color(0xFF7A3E00)
)

private val HighContrastDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFF7DC4FF),
    tertiary = Color(0xFFFFB46A)
)

@Composable
fun KalPlanTheme(
    choice: ThemeChoice = ThemeChoice.MATERIAL_YOU,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        choice == ThemeChoice.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        choice == ThemeChoice.NEUTRAL_BUSINESS ->
            if (darkTheme) NeutralDark else NeutralLight
        choice == ThemeChoice.TURQUOISE ->
            if (darkTheme) TurquoiseDark else TurquoiseLight
        choice == ThemeChoice.HIGH_CONTRAST ->
            if (darkTheme) HighContrastDark else HighContrastLight
        else ->
            if (darkTheme) KalPlanDark else KalPlanLight
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
