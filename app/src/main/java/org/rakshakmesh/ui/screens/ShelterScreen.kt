package org.rakshakmesh.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.Fix
import org.rakshakmesh.core.NearestShelters
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.ui.DialButton
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.currentLang
import org.rakshakmesh.ui.directionLabel
import org.rakshakmesh.ui.km

/**
 * 3 nearest verified shelters by straight-line distance (FR-SHL-02). If none within 10 km, says
 * so and points to 1077; never guesses (FR-SHL-04).
 */
@Composable
fun ShelterScreen(app: RakshakApp, settings: AppSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shelters by app.db.shelters().observeAll().collectAsStateWithLifecycle(emptyList())
    var fix by remember { mutableStateOf<Fix?>(null) }
    var locating by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(app.location.hasPermission()) }
    var showMap by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(OfflineMapFile.exists(ctx)) }
    var mapProgress by remember { mutableIntStateOf(-1) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val lang = currentLang()

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = it.values.any { g -> g }
        if (!hasPermission) locating = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { OfflineMapFile.import(ctx, uri) }
                .onSuccess { mapReady = true; mapError = null }
                .onFailure { mapError = it.message }
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return@LaunchedEffect
        }
        locating = true
        fix = app.location.lastKnown()
        app.location.currentFix(timeoutMs = 20_000)?.let { fix = it }
        locating = false
    }

    val records = remember(shelters) { shelters.map { it.toRecord() } }
    val nearest = fix?.let { NearestShelters.find(it.lat, it.lon, records) }.orEmpty()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.shelter_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }

        when {
            fix == null && locating -> item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Column { CircularProgressIndicator(); Text(stringResource(R.string.shelter_finding)) }
                }
            }
            fix == null -> item { InfoCard(stringResource(R.string.shelter_no_location)) }
            nearest.isEmpty() -> item {
                InfoCard(stringResource(R.string.shelter_none), color = Color(0xFFFFE0B2)) {
                    DialButton("1077", stringResource(R.string.call_1077), Modifier.fillMaxWidth())
                }
            }
            else -> {
                item { Text(stringResource(R.string.shelter_straight_line), style = MaterialTheme.typography.bodyMedium) }
                items(nearest, key = { it.shelter.id }) { n ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val name = if (lang == "or" && n.shelter.nameOdia.isNotBlank()) n.shelter.nameOdia else n.shelter.name
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.shelter_item, km(n.distanceKm), directionLabel(n.direction)),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                            )
                            Text(n.shelter.village, style = MaterialTheme.typography.bodyMedium)
                            n.shelter.capacity?.let { Text(stringResource(R.string.shelter_capacity, it), style = MaterialTheme.typography.bodyMedium) }
                            Text(stringResource(R.string.shelter_verified, n.shelter.source, n.shelter.verifiedOn), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Offline map (FR-SHL-03).
        item {
            OutlinedButton(onClick = { showMap = !showMap }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (showMap) R.string.shelter_hide_map else R.string.shelter_open_map))
            }
        }
        if (showMap) {
            if (mapReady) {
                item {
                    OfflineMap(fix, records.filter { it.isVerified }, Modifier.fillMaxWidth().height(380.dp))
                    Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                item {
                    InfoCard(stringResource(R.string.map_missing)) {
                        when {
                            mapProgress >= 0 -> Text(stringResource(R.string.map_downloading, mapProgress))
                            settings.mapUrl.isBlank() -> Text(stringResource(R.string.map_no_url), style = MaterialTheme.typography.bodyMedium)
                            else -> OutlinedButton(onClick = {
                                mapProgress = 0
                                scope.launch {
                                    runCatching { OfflineMapFile.download(ctx, settings.mapUrl) { p -> mapProgress = p } }
                                        .onSuccess { mapReady = true; mapError = null }
                                        .onFailure { mapError = it.message }
                                    mapProgress = -1
                                }
                            }) { Text(stringResource(R.string.map_download)) }
                        }
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                            Text(stringResource(R.string.map_import))
                        }
                        mapError?.let { Text(stringResource(R.string.map_failed, it)) }
                    }
                }
            }
        }
        item { DialButton("112", stringResource(R.string.call_112), Modifier.fillMaxWidth().padding(bottom = 16.dp)) }
    }
}
