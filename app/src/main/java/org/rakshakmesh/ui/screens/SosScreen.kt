package org.rakshakmesh.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.Fix
import org.rakshakmesh.core.LocationChooser
import org.rakshakmesh.core.MessageType
import org.rakshakmesh.core.SmsBodyBuilder
import org.rakshakmesh.core.SosDetails
import org.rakshakmesh.core.SosKind
import org.rakshakmesh.core.SosLocation
import org.rakshakmesh.data.OutboxRepo
import org.rakshakmesh.ui.BackBar
import org.rakshakmesh.ui.DialButton
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.ageText
import org.rakshakmesh.ui.theme.SosRed

/**
 * SOS in 3 taps: SOS (home) → what is happening → Send (FR-SOS-02).
 * GPS starts as soon as the screen opens so it is usually ready by the time the user taps Send.
 * No internet and no AI anywhere on this path.
 */
@Composable
fun SosScreen(app: RakshakApp, onBack: () -> Unit, onOutbox: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf<SosKind?>(null) }
    var people by remember { mutableIntStateOf(1) }
    var elderly by remember { mutableIntStateOf(0) }
    var location by remember { mutableStateOf<SosLocation?>(null) }
    var result by remember { mutableStateOf<OutboxRepo.HandOff?>(null) }
    var meshCount by remember { mutableStateOf<Int?>(null) }
    var sending by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(app.location.hasPermission()) }
    val fixReady = remember { CompletableDeferred<SosLocation>() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        hasPermission = granted.values.any { it }
        if (!hasPermission && !fixReady.isCompleted) {
            scope.launch {
                val landmark = app.db.profile().get()?.landmark.orEmpty()
                fixReady.complete(LocationChooser.choose(null, null, landmark, System.currentTimeMillis()))
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return@LaunchedEffect
        }
        val last: Fix? = app.location.lastKnown()
        val fresh: Fix? = app.location.currentFix(timeoutMs = 20_000)
        val landmark = app.db.profile().get()?.landmark.orEmpty()
        val chosen = LocationChooser.choose(fresh, last, landmark, System.currentTimeMillis())
        location = chosen
        if (!fixReady.isCompleted) fixReady.complete(chosen)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(stringResource(R.string.sos_title), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindCard(R.string.sos_trapped, Icons.Filled.Waves, kind == SosKind.TRAPPED_BY_WATER) { kind = SosKind.TRAPPED_BY_WATER }
            KindCard(R.string.sos_injured, Icons.Filled.MedicalServices, kind == SosKind.INJURED_MEDICAL) { kind = SosKind.INJURED_MEDICAL }
            KindCard(R.string.sos_evacuate, Icons.AutoMirrored.Filled.DirectionsRun, kind == SosKind.NEED_EVACUATION) { kind = SosKind.NEED_EVACUATION }

            Counter(stringResource(R.string.sos_people), people, 1..99) { people = it }
            Counter(stringResource(R.string.sos_elderly), elderly, 0..99) { elderly = it }

            LocationStatus(location, hasPermission)

            Button(
                enabled = kind != null && !sending,
                onClick = {
                    val k = kind ?: return@Button
                    sending = true
                    scope.launch {
                        val loc = location ?: fixReady.await()
                        val profile = app.db.profile().get()
                        val details = SosDetails(
                            bloodGroup = profile?.bloodGroup, cannotWalk = profile?.cannotWalk == true,
                            pregnant = profile?.pregnant == true, infant = profile?.infant == true,
                        )
                        val landmark = listOfNotNull(profile?.landmark, profile?.village)
                            .filter { it.isNotBlank() }.joinToString(", ")
                        val body = SmsBodyBuilder.sos(k, people, elderly, loc, landmark, details, System.currentTimeMillis())
                        val saved = app.outbox.save(MessageType.SOS, body, app.outbox.recipients(), loc)
                        // Offline mesh first: reaches nearby phones even with no tower at all.
                        val fix = loc.fixOrNull
                        meshCount = app.mesh.sendOwn(org.rakshakmesh.core.mesh.PacketType.SOS, body, fix?.lat, fix?.lon, fix?.accuracyM)
                        result = app.outbox.handToSmsApp(saved.id)
                        sending = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("sos_send"),
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                    Spacer(Modifier.width(12.dp))
                }
                Text(stringResource(R.string.sos_send), style = MaterialTheme.typography.titleMedium)
            }

            result?.let { r ->
                val msg = when (r) {
                    OutboxRepo.HandOff.HANDED -> stringResource(R.string.sos_handed)
                    OutboxRepo.HandOff.NO_SIGNAL -> stringResource(R.string.sos_no_signal)
                    OutboxRepo.HandOff.NO_RECIPIENTS -> stringResource(R.string.sos_no_recipients)
                    OutboxRepo.HandOff.NO_SMS_APP -> stringResource(R.string.sos_no_sms_app)
                }
                val meshLine = meshCount?.let {
                    if (it > 0) stringResource(R.string.mesh_sent_to, it) else stringResource(R.string.mesh_sent_later)
                }.orEmpty()
                InfoCard(listOf(stringResource(R.string.sos_saved), meshLine, msg).filter { it.isNotBlank() }.joinToString("\n"), color = Color(0xFFFFE0B2)) {
                    OutlinedButton(onClick = onOutbox) { Text(stringResource(R.string.open_outbox)) }
                }
            }

            DialButton("112", stringResource(R.string.call_112), Modifier.fillMaxWidth(), color = SosRed)
            Text(stringResource(R.string.no_rescue_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KindCard(label: Int, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        border = if (selected) BorderStroke(4.dp, SosRed) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SosRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = SosRed)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun Counter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = { onChange((value - 1).coerceIn(range)) }) { Text("−") }
        Text("  $value  ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { onChange((value + 1).coerceIn(range)) }) { Text("+") }
    }
}

@Composable
private fun LocationStatus(location: SosLocation?, hasPermission: Boolean) {
    val text = when {
        !hasPermission && location == null -> stringResource(R.string.sos_location_permission)
        location == null -> stringResource(R.string.sos_getting_location)
        location is SosLocation.Fresh -> stringResource(R.string.sos_location_fresh, location.fix.accuracyM?.toInt() ?: 0)
        location is SosLocation.Stale -> stringResource(R.string.sos_location_stale, ageText(location.ageSec / 60))
        else -> stringResource(R.string.sos_location_none)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (location == null && hasPermission) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Default)
    }
}
