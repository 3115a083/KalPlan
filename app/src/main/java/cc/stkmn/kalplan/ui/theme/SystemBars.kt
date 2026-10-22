package cc.stkmn.kalplan.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

private fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** An app-selected theme may differ from Android's theme. Floating/full-screen windows own flags too. */
@Composable
fun SyncSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.activity()
    val background = MaterialTheme.colorScheme.background
    val darkIcons = background.luminance() > 0.179f
    var parent: android.view.ViewParent? = view.parent
    var dialogWindow: Window? = null
    while (parent != null && dialogWindow == null) {
        dialogWindow = (parent as? DialogWindowProvider)?.window
        parent = parent.parent
    }
    val window = dialogWindow ?: activity?.window ?: return
    val isActivity = dialogWindow == null
    SideEffect {
        if (isActivity && activity != null) {
            val scrim = background.toArgb()
            val style = if (darkIcons) SystemBarStyle.light(scrim, scrim) else SystemBarStyle.dark(scrim)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
