package cc.stkmn.kalplan.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppointmentKindIcon(location: String?, mode: String? = null, modifier: Modifier = Modifier) {
    val online = mode in setOf("ONLINE", "HYBRID") || location?.let {
        Regex("(?i)(\\bonline\\b|\\bremote\\b|\\bvideokonferenz\\b|teams\\.microsoft|zoom\\.us|meet\\.google)").containsMatchIn(it)
    } == true
    Icon(when { online -> Icons.Outlined.Videocam; location.isNullOrBlank() -> Icons.Outlined.LocationOff; else -> Icons.Outlined.LocationOn },
        when { online -> tr("Online-Termin", "Online appointment"); location.isNullOrBlank() -> tr("Ort nicht sichtbar oder nicht angegeben", "Location hidden or unspecified"); else -> tr("Termin mit Ort", "Appointment with location") }, modifier)
}
