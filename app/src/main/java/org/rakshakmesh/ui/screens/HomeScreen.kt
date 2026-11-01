package org.rakshakmesh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.AlertFeedParser
import org.rakshakmesh.core.AlertLevel
import org.rakshakmesh.core.NearbyShelter
import org.rakshakmesh.core.NearestShelters
import org.rakshakmesh.core.OutboxState
import org.rakshakmesh.core.PlaceType
import org.rakshakmesh.data.AlertCache
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.device.Phone
import org.rakshakmesh.mesh.MeshState
import org.rakshakmesh.ui.DialButton
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.Routes
import org.rakshakmesh.ui.directionLabel
import org.rakshakmesh.ui.km
import org.rakshakmesh.ui.theme.SafeGreen
import org.rakshakmesh.ui.theme.SosRed
import org.rakshakmesh.ui.theme.levelColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(app: RakshakApp, settings: AppSettings, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val profile by app.db.profile().observe().collectAsStateWithLifecycle(null)
    val district = profile?.district.orEmpty()
    val alerts by remember(district) {
        if (district.isBlank()) flowOf(emptyList<AlertCache>()) else app.db.alerts().observe(district)
    }.collectAsStateWithLifecycle(emptyList())
    val outbox by app.db.outbox().observeAll().collectAsStateWithLifecycle(emptyList())
    val shelters by app.db.shelters().observeAll().collectAsStateWithLifecycle(emptyList())
    val meshState by app.mesh.state.collectAsStateWithLifecycle()
    val peers by app.mesh.peers.collectAsStateWithLifecycle()
    val sosNearby by remember { app.db.mesh().observeActiveSos(System.currentTimeMillis()) }.collectAsStateWithLifecycle(emptyList())
    val unsent = outbox.count { OutboxState.valueOf(it.state).notSentYet }
    val highest = AlertFeedParser.highestActiveOf(alerts.map { it.level to it.validUntil }, System.currentTimeMillis())

    var online by remember { mutableStateOf(Phone.isOnline(ctx)) }
    var battery by remember { mutableStateOf(Phone.batteryPercent(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            online = Phone.isOnline(ctx); battery = Phone.batteryPercent(ctx); delay(15_000)
        }
    }

    // Nearest shelter for the warning card, from last known location only.
    var nearest by remember { mutableStateOf<NearbyShelter?>(null) }
    LaunchedEffect(shelters, highest) {
        if (highest != null && highest.rank >= AlertLevel.ORANGE.rank) {
            app.location.lastKnown()?.let { fix ->
                nearest = NearestShelters.find(
                    fix.lat, fix.lon, shelters.map { it.toRecord() }, limit = 1, maxKm = 25.0,
                    types = setOf(PlaceType.CYCLONE_SHELTER, PlaceType.FLOOD_SHELTER),
                ).firstOrNull()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { onNavigate(Routes.SETTINGS) }, modifier = Modifier.testTag("open_settings")) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.open_settings))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status chips: what works right now.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusChip(
                    if (online) stringResource(R.string.status_online) else stringResource(R.string.status_offline),
                    if (online) Icons.Filled.Wifi else Icons.Filled.CloudOff,
                    if (online) SafeGreen else Color(0xFF757575),
                )
                val meshOn = meshState == MeshState.ON
                StatusChip(
                    if (meshOn) stringResource(R.string.status_mesh_on, peers.size) else stringResource(R.string.status_mesh_off),
                    if (meshOn) Icons.Filled.WifiTethering else Icons.Filled.PortableWifiOff,
                    if (meshOn) SafeGreen else Color(0xFF757575),
                ) { onNavigate(Routes.NEARBY) }
                battery?.let {
                    StatusChip(stringResource(R.string.battery_level, it), Icons.Filled.BatteryStd, if (it <= 30) SosRed else Color(0xFF757575))
                }
            }

            if (settings.cycloneMode) InfoCard(stringResource(R.string.cyclone_mode_on))

            if (sosNearby.any { !it.outgoing }) {
                val n = sosNearby.count { !it.outgoing }
                InfoCard(stringResource(R.string.home_sos_nearby, n), color = SosRed.copy(alpha = 0.18f)) {
                    TextButton(onClick = { onNavigate(Routes.NEARBY) }) { Text(stringResource(R.string.open_nearby)) }
                }
            }

            // Official warning banner.
            if (highest != null && highest.rank >= AlertLevel.ORANGE.rank) {
                val title = stringResource(if (highest == AlertLevel.RED) R.string.red_card_title else R.string.orange_card_title, district)
                val shelterLine = nearest?.let {
                    stringResource(R.string.red_card_shelter, it.shelter.name, km(it.distanceKm), directionLabel(it.direction))
                } ?: stringResource(R.string.red_card_no_shelter)
                InfoCard("$title\n$shelterLine", color = levelColor(highest.name).copy(alpha = 0.25f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onNavigate(Routes.MAP) }) { Text(stringResource(R.string.tab_map)) }
                        TextButton(onClick = { onNavigate(Routes.CHECKLIST) }) { Text(stringResource(R.string.open_checklist)) }
                    }
                }
            }

            if (unsent > 0) {
                InfoCard(stringResource(R.string.unsent_banner, unsent), color = Color(0xFFFFE0B2)) {
                    TextButton(onClick = { onNavigate(Routes.OUTBOX) }) { Text(stringResource(R.string.open_outbox)) }
                }
            }

            val sosLabel = stringResource(R.string.sos_hint)
            Button(
                onClick = { onNavigate(Routes.SOS) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(0.62f).aspectRatio(1f)
                    .testTag("sos_button").semantics { contentDescription = sosLabel },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sos_button), fontSize = 54.sp, fontWeight = FontWeight.Black)
                    Text(sosLabel, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }

            DialButton("112", stringResource(R.string.call_112), Modifier.fillMaxWidth(), color = SosRed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DialButton("1070", stringResource(R.string.call_1070), Modifier.weight(1f))
                DialButton("1077", stringResource(R.string.call_1077), Modifier.weight(1f))
            }

            // Big, labelled tiles for everything else.
            val tiles = listOf(
                Triple(R.string.tile_nearby, Icons.Filled.WifiTethering, Routes.NEARBY),
                Triple(R.string.tile_map, Icons.Filled.Map, Routes.MAP),
                Triple(R.string.tile_safe, Icons.Filled.CheckCircle, Routes.FAMILY),
                Triple(R.string.tile_alerts, Icons.Filled.Campaign, Routes.ALERTS),
                Triple(R.string.tile_checklist, Icons.Filled.Checklist, Routes.CHECKLIST),
                Triple(R.string.tile_outbox, Icons.Filled.Outbox, Routes.OUTBOX),
            )
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (label, icon, r) ->
                        Tile(stringResource(label), icon, Modifier.weight(1f).testTag("tile_$r")) { onNavigate(r) }
                    }
                }
            }

            Text(
                stringResource(R.string.no_rescue_note),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            if ((battery ?: 100) <= 30) InfoCard(stringResource(R.string.battery_tip), color = Color(0xFFFFF59D))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit = {}) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun Tile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(4.dp).height(6.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
