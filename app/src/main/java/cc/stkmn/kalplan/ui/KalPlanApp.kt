package cc.stkmn.kalplan.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.R

private enum class MainSection(
    @StringRes val label: Int
) {
    REQUESTS(R.string.nav_requests),
    CALENDAR(R.string.nav_calendar),
    UNCLEAR(R.string.nav_unclear),
    SETTINGS(R.string.nav_settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalPlanApp() {
    var section by rememberSaveable { mutableStateOf(MainSection.REQUESTS) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("KalPlan") })
        },
        bottomBar = {
            NavigationBar {
                MainSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    MainSection.REQUESTS -> Icons.Outlined.Inbox
                                    MainSection.CALENDAR -> Icons.Outlined.CalendarMonth
                                    MainSection.UNCLEAR -> Icons.Outlined.HelpOutline
                                    MainSection.SETTINGS -> Icons.Outlined.Settings
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(item.label)) }
                    )
                }
            }
        }
    ) { padding ->
        when (section) {
            MainSection.REQUESTS -> RequestsScreen(padding)
            MainSection.CALENDAR -> PlaceholderScreen(
                padding,
                R.string.calendar_title,
                R.string.calendar_placeholder
            )
            MainSection.UNCLEAR -> PlaceholderScreen(
                padding,
                R.string.unclear_title,
                R.string.unclear_placeholder
            )
            MainSection.SETTINGS -> SettingsScreen(padding)
        }
    }
}

@Composable
private fun RequestsScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.requests_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.requests_empty),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PlaceholderScreen(
    padding: PaddingValues,
    @StringRes title: Int,
    @StringRes body: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Text(stringResource(body))
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        ListItem(headlineContent = { Text(stringResource(R.string.settings_sync)) })
        ListItem(headlineContent = { Text(stringResource(R.string.settings_duration)) })
        ListItem(headlineContent = { Text(stringResource(R.string.settings_origin)) })
        ListItem(headlineContent = { Text(stringResource(R.string.settings_offline_distance)) })
    }
}
