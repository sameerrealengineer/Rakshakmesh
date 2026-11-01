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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.rakshakmesh.ui.theme.levelColor

/**
 * Official alerts only (IMD / SACHET), with source and time. Cached copy is shown offline with
 * its age; staleness is never hidden (FR-ALR-03, FR-ALR-04).
 */
@Composable
fun AlertsScreen(app: RakshakApp, settings: AppSettings) {
    val ctx = LocalContext.current
    val profile by app.db.profile().observe().collectAsStateWithLifecycle(null)
    val district = profile?.district.orEmpty()
    val alerts by remember(district) {
        if (district.isBlank()) flowOf(emptyList<AlertCache>()) else app.db.alerts().observe(district)
    }.collectAsStateWithLifecycle(emptyList())

    // Re-render the age label every minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    val active = alerts.filter { it.validUntil == null || it.validUntil > now }
        .sortedByDescending { AlertLevel.parse(it.level).rank }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                stringResource(R.string.alerts_title, district),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item {
            val fetched = settings.lastAlertFetch
            val line = if (fetched == 0L) {
                stringResource(R.string.alerts_never)
            } else {
                val age = Staleness.age(fetched, now)
                if (age.isStale) stringResource(R.string.alerts_stale, ageText(age.minutes))
                else stringResource(R.string.alerts_updated, ageText(age.minutes))
            }
            val stale = settings.lastAlertFetch == 0L || Staleness.age(settings.lastAlertFetch, now).isStale
            InfoCard(line, color = if (stale) Color(0xFFFFE0B2) else MaterialTheme.colorScheme.surfaceVariant) {
                settings.lastAlertError?.let { Text(stringResource(R.string.alerts_error, it), style = MaterialTheme.typography.bodyMedium) }
                if (settings.cycloneMode) {
                    Text(stringResource(R.string.alerts_refresh_off), style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedButton(onClick = { AlertScheduler.refreshNow(ctx) }) { Text(stringResource(R.string.alerts_refresh)) }
                }
            }
        }
        if (active.isEmpty() && settings.lastAlertFetch != 0L) {
            item { InfoCard(stringResource(R.string.alerts_none)) }
        }
        items(active, key = { it.alertId }) { AlertCard(it) }
        item { HorizontalDivider() }
        item { Text(stringResource(R.string.alerts_sachet_note), style = MaterialTheme.typography.bodyMedium) }
        item {
            TextButton(onClick = {
                Phone.openUrl(ctx, "https://play.google.com/store/search?q=SATARK%20OSDMA&c=apps")
            }) { Text(stringResource(R.string.open_satark)) }
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
            Text(stringResource(R.string.alert_source, listOfNotNull(a.agency, a.source).distinct().joinToString(" / ")), style = MaterialTheme.typography.bodyMedium)
            a.issuedAt?.let { Text(stringResource(R.string.alert_issued, formatDateTime(it)), style = MaterialTheme.typography.bodyMedium) }
            a.validUntil?.let { Text(stringResource(R.string.alert_valid, formatDateTime(it)), style = MaterialTheme.typography.bodyMedium) }
            if (a.originalText.isNotBlank()) Text(a.originalText, style = MaterialTheme.typography.bodyLarge)
            // Simplified Odia only below the original, clearly labelled (FR-ALR-03, PRD 7.4).
            a.simplifiedOdia?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text(stringResource(R.string.alert_simplified_label), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
