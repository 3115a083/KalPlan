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
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E0FF),
    onPrimaryContainer = Color(0xFF15145A),
    secondary = Color(0xFF167B72),
    secondaryContainer = Color(0xFFB8F0E8),
    tertiary = Color(0xFF9A5B00),
    tertiaryContainer = Color(0xFFFFDDB4),
    background = Color(0xFFF9F8FF),
    surface = Color(0xFFF9F8FF),
    surfaceVariant = Color(0xFFE5E1EC),
    surfaceContainerLow = Color(0xFFF3F2FA),
    surfaceContainer = Color(0xFFEDECF4),
    surfaceContainerHigh = Color(0xFFE7E6EE)
)

private val KalPlanDark = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF20246F),
    primaryContainer = Color(0xFF383B87),
    secondary = Color(0xFF7AD9CE),
    secondaryContainer = Color(0xFF005049),
    tertiary = Color(0xFFFFB95F),
    tertiaryContainer = Color(0xFF6F4100),
    background = Color(0xFF111117),
    surface = Color(0xFF111117),
    surfaceContainerLow = Color(0xFF19191F),
    surfaceContainer = Color(0xFF1D1D24),
    surfaceContainerHigh = Color(0xFF28282F)
)

private val NeutralLight = lightColorScheme(
    primary = Color(0xFF4D5D6C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3F2),
    secondary = Color(0xFF59636C),
    tertiary = Color(0xFF6B5F55),
    background = Color(0xFFF7F9FA),
    surface = Color(0xFFF7F9FA),
    surfaceContainerLow = Color(0xFFF0F3F5),
    surfaceContainer = Color(0xFFE9EDF0),
    surfaceContainerHigh = Color(0xFFE2E7EA)
)

private val NeutralDark = darkColorScheme(
    primary = Color(0xFFC4D4E3),
    onPrimary = Color(0xFF26323C),
    primaryContainer = Color(0xFF354552),
    secondary = Color(0xFFC1CBD4),
    tertiary = Color(0xFFD7C5B8),
    background = Color(0xFF101416),
    surface = Color(0xFF101416),
    surfaceContainerLow = Color(0xFF181C1F),
    surfaceContainer = Color(0xFF1D2225),
    surfaceContainerHigh = Color(0xFF282D31)
)

private val TurquoiseLight = lightColorScheme(
    primary = Color(0xFF006B62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E6),
    secondary = Color(0xFF4A635F),
    secondaryContainer = Color(0xFFCDE8E2),
    tertiary = Color(0xFF456179),
    tertiaryContainer = Color(0xFFCBE6FF),
    background = Color(0xFFF3FBF8),
    surface = Color(0xFFF3FBF8),
    surfaceContainerLow = Color(0xFFECF5F2),
    surfaceContainer = Color(0xFFE4EEEB),
    surfaceContainerHigh = Color(0xFFDCE7E4)
)

private val TurquoiseDark = darkColorScheme(
    primary = Color(0xFF54DBCD),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    secondary = Color(0xFFB1CCC7),
    tertiary = Color(0xFFADC9E5),
    background = Color(0xFF0D1513),
    surface = Color(0xFF0D1513),
    surfaceContainerLow = Color(0xFF151D1B),
    surfaceContainer = Color(0xFF192220),
    surfaceContainerHigh = Color(0xFF242D2A)
)

private val HighContrastLight = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF005A9C),
    tertiary = Color(0xFF7A3E00),
    background = Color.White,
    surface = Color.White,
    surfaceContainerLow = Color(0xFFF2F2F2),
    surfaceContainer = Color(0xFFE5E5E5),
    surfaceContainerHigh = Color(0xFFD8D8D8),
    outline = Color.Black
)

private val HighContrastDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFF7DC4FF),
    tertiary = Color(0xFFFFB46A),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLow = Color(0xFF151515),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF303030),
    outline = Color.White
)

@Composable
fun KalPlanTheme(
    choice: ThemeChoice = ThemeChoice.MATERIAL_YOU,
    darkTheme: Boolean = isSystemInDarkTheme(),
    primaryHex: String = "",
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
        colorScheme = if (primaryHex.matches(Regex("[0-9a-fA-F]{6}"))) {
            val custom = Color(android.graphics.Color.parseColor("#" + primaryHex))
            val luminance = 0.2126 * custom.red + 0.7152 * custom.green + 0.0722 * custom.blue
            scheme.copy(primary = custom, onPrimary = if (luminance > 0.5) Color.Black else Color.White)
        } else scheme,
        content = content
    )
}

