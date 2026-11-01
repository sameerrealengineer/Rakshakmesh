package org.rakshakmesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.alerts.AlertScheduler
import org.rakshakmesh.core.AlertLevel
import org.rakshakmesh.core.Staleness
import org.rakshakmesh.data.AlertCache
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.device.Phone
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.ageText
import org.rakshakmesh.ui.formatDateTime
import org.rakshakmesh.ui.levelLabel
import org.rakshakmesh.ui.theme.SosRed
import org.rakshakmesh.ui.theme.levelColor

/**
 * Official alerts (IMD / NDMA SACHET) for the user's district, saved for offline use, plus
 * alerts passed on by nearby phones. Always shows a built-in safety guide that needs nothing.
 * Never shows technical errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(app: RakshakApp, settings: AppSettings, onChecklist: () -> Unit) {
    val ctx = LocalContext.current
    val profile by app.db.profile().observe().collectAsStateWithLifecycle(null)
    val district = profile?.district.orEmpty()
    val alerts by remember(district) {
        if (district.isBlank()) flowOf(emptyList<AlertCache>()) else app.db.alerts().observe(district)
    }.collectAsStateWithLifecycle(emptyList())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    val active = alerts.filter { it.validUntil == null || it.validUntil > now }
        .sortedByDescending { AlertLevel.parse(it.level).rank }
    val online = Phone.isOnline(ctx)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.alerts_title, district)) },
            actions = {
                if (!settings.cycloneMode) {
                    IconButton(onClick = { AlertScheduler.refreshNow(ctx) }, modifier = Modifier.testTag("alerts_refresh")) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.alerts_refresh))
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val fetched = settings.lastAlertFetch
                val age = if (fetched > 0) Staleness.age(fetched, now) else null
                val line = when {
                    settings.cycloneMode -> stringResource(R.string.alerts_refresh_off)
                    !online && age == null -> stringResource(R.string.alerts_offline_never)
                    !online -> stringResource(R.string.alerts_offline_saved, ageText(age!!.minutes))
                    age == null && settings.lastAlertError != null -> stringResource(R.string.alerts_unreachable)
                    age == null -> stringResource(R.string.alerts_loading)
                    settings.alertSourceDown -> stringResource(R.string.alerts_source_down, ageText(age.minutes))
                    age.isStale -> stringResource(R.string.alerts_stale, ageText(age.minutes))
                    else -> stringResource(R.string.alerts_updated, ageText(age.minutes))
                }
                val warn = !online || age == null || age.isStale || settings.alertSourceDown
                InfoCard(line, color = if (warn) Color(0xFFFFE0B2) else MaterialTheme.colorScheme.surfaceVariant)
            }
            if (active.isEmpty() && settings.lastAlertFetch != 0L) {
                item { InfoCard(stringResource(R.string.alerts_none)) }
            }
            items(active, key = { it.alertId }) { AlertCard(it) }
            item { SafetyGuide(onChecklist) }
            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.alerts_sachet_note),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SafetyGuide(onChecklist: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.08f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleMedium)
            listOf(R.string.guide_1, R.string.guide_2, R.string.guide_3, R.string.guide_4, R.string.guide_5, R.string.guide_6).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onChecklist) { Text(stringResource(R.string.open_checklist)) }
        }
    }
}

@Composable
private fun AlertCard(a: AlertCache) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).background(levelColor(a.level), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(levelLabel(a.level), fontWeight = FontWeight.Bold, color = levelColor(a.level))
            }
            Text(a.headline, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.alert_source, listOfNotNull(a.agency, a.source).distinct().joinToString(" / ")),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (a.viaMesh) Text(stringResource(R.string.alert_via_mesh), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            a.issuedAt?.let { Text(stringResource(R.string.alert_issued, formatDateTime(it)), style = MaterialTheme.typography.bodyMedium) }
            a.validUntil?.let { Text(stringResource(R.string.alert_valid, formatDateTime(it)), style = MaterialTheme.typography.bodyMedium) }
            if (a.originalText.isNotBlank()) Text(a.originalText, style = MaterialTheme.typography.bodyLarge)
            a.simplifiedOdia?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text(stringResource(R.string.alert_simplified_label), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
