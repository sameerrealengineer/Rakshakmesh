package org.rakshakmesh.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.Districts
import org.rakshakmesh.core.Fix
import org.rakshakmesh.core.Geo
import org.rakshakmesh.core.NearestShelters
import org.rakshakmesh.core.PlaceType
import org.rakshakmesh.core.ShelterRecord
import org.rakshakmesh.mesh.Permissions
import org.rakshakmesh.ui.DialButton
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.directionLabel
import org.rakshakmesh.ui.km
import org.rakshakmesh.ui.theme.SosRed
import java.io.File

private enum class Layer(val label: Int, val types: Set<PlaceType>) {
    SHELTERS(R.string.layer_shelters, setOf(PlaceType.CYCLONE_SHELTER, PlaceType.FLOOD_SHELTER)),
    HOSPITALS(R.string.layer_hospitals, setOf(PlaceType.HOSPITAL)),
    POLICE(R.string.layer_police, setOf(PlaceType.POLICE)),
}

/** A thing the user tapped: a safe place or a nearby SOS. */
private data class Selected(val id: String, val name: String, val kind: String, val lat: Double, val lon: Double, val source: String?, val community: Boolean)

/**
 * Offline map of Odisha with shelters, hospitals, police stations and SOS from nearby phones.
 * Tap a dot to see the distance and an arrow that points the way (works without internet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(app: RakshakApp) {
    val ctx = LocalContext.current
    val shelters by app.db.shelters().observeAll().collectAsStateWithLifecycle(emptyList())
    val sosList by remember { app.db.mesh().observeActiveSos(System.currentTimeMillis()) }.collectAsStateWithLifecycle(emptyList())
    val profile by app.db.profile().observe().collectAsStateWithLifecycle(null)
    var pmtiles by remember { mutableStateOf<File?>(null) }
    var preparing by remember { mutableStateOf(true) }
    var me by remember { mutableStateOf<Fix?>(null) }
    var layers by remember { mutableStateOf(Layer.entries.toSet()) }
    var showSos by remember { mutableStateOf(true) }
    var listMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Selected?>(null) }
    var camera by remember { mutableStateOf<CameraTarget?>(null) }
    var seq by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(app.location.hasPermission()) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = it.values.any { g -> g }
    }

    LaunchedEffect(Unit) {
        pmtiles = MapAssets.ensure(ctx)
        preparing = false
    }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            return@LaunchedEffect
        }
        app.location.lastKnown()?.let { me = it; camera = CameraTarget(it.lat, it.lon, 13.0, seq++) }
        app.location.currentFix(20_000)?.let {
            val first = me == null
            me = it
            if (first) camera = CameraTarget(it.lat, it.lon, 13.0, seq++)
        }
    }

    val records = remember(shelters) { shelters.map { it.toRecord() }.filter { it.isVerified } }
    val visibleTypes = layers.flatMap { it.types }.toSet()
    val placesJson = remember(records, visibleTypes) {
        MapAssets.collection(
            records.filter { it.placeType in visibleTypes }.map {
                MapAssets.point(it.lon, it.lat, mapOf("id" to "p:${it.id}", "name" to it.name, "kind" to it.placeType.code))
            },
        )
    }
    val sosJson = remember(sosList, showSos) {
        MapAssets.collection(
            if (!showSos) emptyList() else sosList.filter { !it.outgoing && it.lat != null && it.lon != null }.map {
                MapAssets.point(it.lon!!, it.lat!!, mapOf("id" to "s:${it.id}", "name" to "SOS ${it.originName}"))
            },
        )
    }
    val meJson = remember(me) { MapAssets.collection(listOfNotNull(me?.let { MapAssets.point(it.lon, it.lat, mapOf("id" to "me")) })) }
    val start = me?.let { it.lat to it.lon }
        ?: profile?.district?.let { Districts.HQ[it] }
        ?: (20.30 to 85.83)

    fun select(id: String) {
        selected = when {
            id.startsWith("p:") -> records.firstOrNull { it.id == id.removePrefix("p:") }?.let { r ->
                Selected(id, r.name, r.placeType.code, r.lat, r.lon, r.source, r.isCommunityData)
            }
            id.startsWith("s:") -> sosList.firstOrNull { it.id == id.removePrefix("s:") }?.let { m ->
                Selected(id, "SOS · ${m.originName}", "sos", m.lat ?: return@let null, m.lon ?: return@let null, null, false)
            }
            else -> null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.map_title)) },
            actions = {
                IconButton(onClick = { listMode = !listMode }, modifier = Modifier.testTag("map_list_toggle")) {
                    Icon(if (listMode) Icons.Filled.Map else Icons.AutoMirrored.Filled.List, stringResource(R.string.map_list))
                }
            },
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Layer.entries.forEach { l ->
                FilterChip(selected = l in layers, onClick = { layers = if (l in layers) layers - l else layers + l }, label = { Text(stringResource(l.label)) })
            }
            FilterChip(selected = showSos, onClick = { showSos = !showSos }, label = { Text(stringResource(R.string.layer_sos, sosList.count { !it.outgoing })) })
        }

        if (listMode) {
            PlaceList(me, records, onPick = { select(it); listMode = false; selected?.let { s -> camera = CameraTarget(s.lat, s.lon, 15.0, seq++) } })
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (preparing) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.map_preparing))
                }
            } else {
                OfflineMap(
                    pmtiles = pmtiles, startLat = start.first, startLon = start.second,
                    places = placesJson, sos = sosJson, me = meJson, camera = camera,
                    onFeatureClick = ::select, modifier = Modifier.fillMaxSize().testTag("map_view"),
                )
                if (pmtiles == null) {
                    Box(Modifier.align(Alignment.TopCenter).padding(12.dp)) { InfoCard(stringResource(R.string.map_not_bundled)) }
                }
                Text(
                    stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
            FloatingActionButton(
                onClick = {
                    val m = me
                    if (m != null) camera = CameraTarget(m.lat, m.lon, 14.0, seq++)
                    else if (!Permissions.granted(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)) hasPermission = false
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.MyLocation, stringResource(R.string.map_my_location)) }

            selected?.let { s ->
                SelectedCard(s, me, onClose = { selected = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }
        }
    }
}

@Composable
private fun PlaceList(me: Fix?, records: List<ShelterRecord>, onPick: (String) -> Unit) {
    if (me == null) {
        Column(Modifier.padding(16.dp)) { InfoCard(stringResource(R.string.shelter_no_location)) }
        return
    }
    val shelters = NearestShelters.find(me.lat, me.lon, records, limit = 5, maxKm = 25.0, types = Layer.SHELTERS.types)
    val hospitals = NearestShelters.find(me.lat, me.lon, records, limit = 3, maxKm = 50.0, types = Layer.HOSPITALS.types)
    val police = NearestShelters.find(me.lat, me.lon, records, limit = 2, maxKm = 50.0, types = Layer.POLICE.types)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.shelter_straight_line), style = MaterialTheme.typography.bodyMedium) }
        item { Text(stringResource(R.string.layer_shelters), style = MaterialTheme.typography.titleMedium) }
        if (shelters.none { it.distanceKm <= 10.0 }) {
            item {
                InfoCard(stringResource(R.string.shelter_none), color = Color(0xFFFFE0B2)) {
                    DialButton("1077", stringResource(R.string.call_1077), Modifier.fillMaxWidth())
                }
            }
        }
        listOf(shelters, hospitals, police).forEachIndexed { i, group ->
            if (i == 1) item { Text(stringResource(R.string.layer_hospitals), style = MaterialTheme.typography.titleMedium) }
            if (i == 2) item { Text(stringResource(R.string.layer_police), style = MaterialTheme.typography.titleMedium) }
            items(group, key = { "${i}_${it.shelter.id}" }) { n ->
                Card(onClick = { onPick("p:${n.shelter.id}") }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.shelter.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.shelter_item, km(n.distanceKm), directionLabel(n.direction)), fontWeight = FontWeight.Bold)
                        Text(sourceLine(n.shelter.source, n.shelter.isCommunityData), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceLine(source: String?, community: Boolean): String = when {
    source == null -> stringResource(R.string.map_source_mesh)
    community -> stringResource(R.string.map_source_osm)
    else -> stringResource(R.string.alert_source, source)
}

@Composable
private fun SelectedCard(s: Selected, me: Fix?, onClose: () -> Unit, modifier: Modifier) {
    val heading = rememberHeading()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = if (s.kind == "sos") SosRed else Color.Unspecified)
                Text(kindLabel(s.kind), style = MaterialTheme.typography.bodyMedium)
                if (me != null) {
                    val d = Geo.distanceKm(me.lat, me.lon, s.lat, s.lon)
                    val b = Geo.bearingDeg(me.lat, me.lon, s.lat, s.lon)
                    Text(stringResource(R.string.shelter_item, km(d), directionLabel(Geo.compass(b))), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.shelter_straight_line), style = MaterialTheme.typography.bodySmall)
                }
                Text(sourceLine(s.source, s.community), style = MaterialTheme.typography.bodySmall)
            }
            if (me != null) {
                val b = Geo.bearingDeg(me.lat, me.lon, s.lat, s.lon).toFloat()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Navigation, contentDescription = stringResource(R.string.map_arrow),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp).rotate(b - (heading ?: 0f)),
                    )
                    if (heading == null) Text(stringResource(R.string.map_arrow_north), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "cyclone_shelter" -> R.string.kind_cyclone_shelter
        "flood_shelter" -> R.string.kind_flood_shelter
        "hospital" -> R.string.kind_hospital
        "police" -> R.string.kind_police
        "sos" -> R.string.mesh_kind_sos
        else -> R.string.kind_other
    },
)

/** Phone compass heading in degrees (0 = north), or null if the phone has no compass. */
@Composable
fun rememberHeading(): Float? {
    val ctx = LocalContext.current
    var heading by remember { mutableFloatStateOf(Float.NaN) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val deg = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
                // Light smoothing so the arrow does not shake.
                heading = if (heading.isNaN()) deg else {
                    val diff = ((deg - heading + 540) % 360) - 180
                    (heading + diff * 0.2f + 360) % 360
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return heading.takeIf { !it.isNaN() }
}
