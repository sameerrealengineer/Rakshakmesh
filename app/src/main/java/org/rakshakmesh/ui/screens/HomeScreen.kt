package org.rakshakmesh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.AlertFeedParser
import org.rakshakmesh.core.AlertLevel
import org.rakshakmesh.core.NearbyShelter
import org.rakshakmesh.core.NearestShelters
import org.rakshakmesh.core.OutboxState
import org.rakshakmesh.data.AlertCache
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.device.Phone
import org.rakshakmesh.ui.DialButton
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.Routes
import org.rakshakmesh.ui.directionLabel
import org.rakshakmesh.ui.km
import org.rakshakmesh.ui.theme.SosRed
import org.rakshakmesh.ui.theme.levelColor

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
    val unsent = outbox.count { OutboxState.valueOf(it.state).notSentYet }
    val highest = AlertFeedParser.highestActiveOf(alerts.map { it.level to it.validUntil }, System.currentTimeMillis())
    val battery = remember { Phone.batteryPercent(ctx) }

    // Nearest shelter for the warning card, from last known location only (no GPS switched on here).
    var nearest by remember { mutableStateOf<NearbyShelter?>(null) }
    LaunchedEffect(shelters, highest) {
        if (highest != null && highest.rank >= AlertLevel.ORANGE.rank) {
            app.location.lastKnown()?.let { fix ->
                nearest = NearestShelters.find(fix.lat, fix.lon, shelters.map { it.toRecord() }, limit = 1).firstOrNull()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (settings.cycloneMode) InfoCard(stringResource(R.string.cyclone_mode_on))

        if (unsent > 0) {
            InfoCard(stringResource(R.string.unsent_banner, unsent), color = Color(0xFFFFE0B2)) {
                TextButton(onClick = { onNavigate(Routes.OUTBOX) }) { Text(stringResource(R.string.open_outbox)) }
            }
        }

        // Red/orange warning action card (FR-ALR-05).
        if (highest != null && highest.rank >= AlertLevel.ORANGE.rank) {
            val title = stringResource(
                if (highest == AlertLevel.RED) R.string.red_card_title else R.string.orange_card_title, district,
            )
            val shelterLine = nearest?.let {
                stringResource(R.string.red_card_shelter, it.shelter.name, km(it.distanceKm), directionLabel(it.direction))
            } ?: stringResource(R.string.red_card_no_shelter)
            InfoCard("$title\n$shelterLine", color = levelColor(highest.name).copy(alpha = 0.25f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate(Routes.SHELTER) }) { Text(stringResource(R.string.tab_shelter)) }
                    OutlinedButton(onClick = { onNavigate(Routes.CHECKLIST) }) { Text(stringResource(R.string.open_checklist)) }
                }
            }
        }

        val sosLabel = stringResource(R.string.sos_hint)
        Button(
            onClick = { onNavigate(Routes.SOS) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(0.7f).aspectRatio(1f)
                .semantics { contentDescription = sosLabel },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.sos_button), fontSize = 56.sp, fontWeight = FontWeight.Black)
                Text(sosLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }

        DialButton("112", stringResource(R.string.call_112), Modifier.fillMaxWidth(), color = SosRed)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DialButton("1070", stringResource(R.string.call_1070), Modifier.weight(1f))
            DialButton("1077", stringResource(R.string.call_1077), Modifier.weight(1f))
        }
        Text(stringResource(R.string.no_rescue_note), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NavTile(stringResource(R.string.open_checklist), Icons.Filled.Checklist, Modifier.weight(1f)) { onNavigate(Routes.CHECKLIST) }
            NavTile(stringResource(R.string.open_outbox), Icons.Filled.Outbox, Modifier.weight(1f)) { onNavigate(Routes.OUTBOX) }
            NavTile(stringResource(R.string.open_settings), Icons.Filled.Settings, Modifier.weight(1f)) { onNavigate(Routes.SETTINGS) }
        }

        // Battery tip (FR-BAT-02).
        battery?.let {
            Text(stringResource(R.string.battery_level, it), style = MaterialTheme.typography.bodyMedium)
            if (it <= 30) InfoCard(stringResource(R.string.battery_tip), color = Color(0xFFFFF59D))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NavTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 72.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
