package org.rakshakmesh.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.device.Phone
import org.rakshakmesh.ui.screens.AlertsScreen
import org.rakshakmesh.ui.screens.ChecklistScreen
import org.rakshakmesh.ui.screens.FamilyScreen
import org.rakshakmesh.ui.screens.HomeScreen
import org.rakshakmesh.ui.screens.MapScreen
import org.rakshakmesh.ui.screens.NearbyScreen
import org.rakshakmesh.ui.screens.OnboardingScreen
import org.rakshakmesh.ui.screens.OutboxScreen
import org.rakshakmesh.ui.screens.SettingsScreen
import org.rakshakmesh.ui.screens.SosScreen

object Routes {
    const val HOME = "home"
    const val NEARBY = "nearby"
    const val MAP = "map"
    const val ALERTS = "alerts"
    const val FAMILY = "family"
    const val SOS = "sos"
    const val OUTBOX = "outbox"
    const val CHECKLIST = "checklist"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
}

/** Opened from a notification: which screen, and optionally an SOS to forward by SMS. */
data class DeepLink(val route: String, val forwardSosId: String?)

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
    Tab(Routes.NEARBY, R.string.tab_nearby, Icons.Filled.WifiTethering),
    Tab(Routes.MAP, R.string.tab_map, Icons.Filled.Map),
    Tab(Routes.ALERTS, R.string.tab_alerts, Icons.Filled.Campaign),
    Tab(Routes.FAMILY, R.string.tab_family, Icons.Filled.FamilyRestroom),
)

fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun RakshakRoot(app: RakshakApp, settings: AppSettings, deepLink: DeepLink?, onLinkHandled: () -> Unit) {
    if (!settings.onboarded) {
        OnboardingScreen(app, isEdit = false, onDone = {})
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val peers by app.mesh.peers.collectAsStateWithLifecycle()

    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        if (tabs.any { it.route == link.route }) nav.goTab(link.route)
        link.forwardSosId?.let { id ->
            // Gateway: this phone has signal, so pass a nearby SOS on by SMS.
            app.db.mesh().get(id)?.let { m ->
                val to = app.outbox.recipients()
                val body = (if (m.body.startsWith("SOS")) m.body else "SOS via RakshakMesh from ${m.originName}: ${m.body}") +
                    (if (m.lat != null && m.lon != null) " | maps.google.com/?q=${m.lat},${m.lon}" else "")
                Phone.composeSms(ctx, to, body.take(300))
            }
        }
        onLinkHandled()
    }

    // When the user comes back with signal, offer to send what is still unsent.
    var unsentCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { unsentCount = if (Phone.isAirplaneModeOn(ctx)) 0 else app.outbox.unsent().size }
    }
    if (unsentCount > 0) {
        AlertDialog(
            onDismissRequest = { unsentCount = 0 },
            title = { Text(stringResource(R.string.unsent_dialog_title)) },
            text = { Text(stringResource(R.string.unsent_dialog_text, unsentCount)) },
            confirmButton = {
                TextButton(onClick = {
                    unsentCount = 0
                    scope.launch { app.outbox.unsent().firstOrNull()?.let { app.outbox.handToSmsApp(it.id) } }
                }) { Text(stringResource(R.string.send_now)) }
            },
            dismissButton = { TextButton(onClick = { unsentCount = 0 }) { Text(stringResource(R.string.later)) } },
        )
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = {
                                if (t.route == Routes.NEARBY && peers.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${peers.size}") } }) { Icon(t.icon, null) }
                                } else {
                                    Icon(t.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(t.label), maxLines = 1) },
                            modifier = Modifier.testTag("tab_${t.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(app, settings, onNavigate = { r ->
                    if (tabs.any { it.route == r }) nav.goTab(r) else nav.navigate(r)
                })
            }
            composable(Routes.NEARBY) { NearbyScreen(app, settings, onShowMap = { nav.goTab(Routes.MAP) }) }
            composable(Routes.MAP) { MapScreen(app) }
            composable(Routes.ALERTS) { AlertsScreen(app, settings, onChecklist = { nav.navigate(Routes.CHECKLIST) }) }
            composable(Routes.FAMILY) { FamilyScreen(app, onNeedHelp = { nav.navigate(Routes.SOS) }, onEditContacts = { nav.navigate(Routes.PROFILE) }) }
            composable(Routes.SOS) { SosScreen(app, onBack = { nav.popBackStack() }, onOutbox = { nav.navigate(Routes.OUTBOX) }) }
            composable(Routes.OUTBOX) { OutboxScreen(app, onBack = { nav.popBackStack() }) }
            composable(Routes.CHECKLIST) { ChecklistScreen(app, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(app, settings, onBack = { nav.popBackStack() }, onEditProfile = { nav.navigate(Routes.PROFILE) })
            }
            composable(Routes.PROFILE) { OnboardingScreen(app, isEdit = true, onDone = { nav.popBackStack() }) }
        }
    }
}
