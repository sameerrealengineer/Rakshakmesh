package org.rakshakmesh.ui.screens

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

/**
 * The offline map of Odisha ships inside the APK (OpenStreetMap data, OpenMapTiles schema,
 * built in CI with Planetiler). On first start it is copied once to app storage, because
 * MapLibre reads PMTiles from a file. Never downloads tiles from the internet.
 */
object MapAssets {
    const val ASSET = "map/odisha.pmtiles"

    fun bundled(context: Context): Boolean =
        runCatching { context.assets.openFd(ASSET).use { it.length > 0 } }.getOrDefault(false) ||
            runCatching { context.assets.open(ASSET).use { it.read() >= 0 } }.getOrDefault(false)

    /** Copies the bundled map to app storage if needed. Returns the file, or null if not bundled. */
    suspend fun ensure(context: Context): File? = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "map/odisha.pmtiles")
        val assetSize = runCatching { context.assets.openFd(ASSET).use { it.length } }.getOrNull()
        if (target.exists() && target.length() > 0 && (assetSize == null || target.length() == assetSize)) return@withContext target
        if (!bundled(context)) return@withContext null
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        runCatching {
            context.assets.open(ASSET).use { i -> tmp.outputStream().use { i.copyTo(it, 256 * 1024) } }
            tmp.renameTo(target)
            target
        }.getOrElse {
            tmp.delete()
            null
        }
    }

    fun hasGlyphs(context: Context) =
        runCatching { context.assets.list("map/glyphs")?.isNotEmpty() == true }.getOrDefault(false)

    fun style(pmtiles: File?, glyphs: Boolean, centerLat: Double, centerLon: Double): String {
        val name = """["coalesce",["get","name:latin"],["get","name_en"],["get","name"]]"""
        val base = if (pmtiles == null) emptyList() else listOf(
            fill("landcover", "landcover", "#dfe9d0", 0.7),
            fill("landuse", "landuse", "#ece7df", 0.6),
            fill("park", "park", "#d4e8c4", 0.6),
            fill("water", "water", "#a8cdf0", 1.0),
            """{"id":"waterway","type":"line","source":"omt","source-layer":"waterway","paint":{"line-color":"#a8cdf0","line-width":["interpolate",["linear"],["zoom"],8,0.6,14,2.5]}}""",
            """{"id":"boundary","type":"line","source":"omt","source-layer":"boundary","filter":["<=",["get","admin_level"],6],"paint":{"line-color":"#9e7ea8","line-width":["match",["get","admin_level"],4,1.6,0.8],"line-dasharray":[3,2]}}""",
            """{"id":"road-casing","type":"line","source":"omt","source-layer":"transportation","filter":["match",["get","class"],["motorway","trunk","primary","secondary","tertiary","minor"],true,false],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#b9b0a6","line-width":["interpolate",["linear"],["zoom"],7,0.8,14,8]}}""",
            """{"id":"road","type":"line","source":"omt","source-layer":"transportation","layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":["match",["get","class"],["motorway","trunk"],"#f7c873","primary","#fbe39a","#ffffff"],"line-width":["interpolate",["linear"],["zoom"],7,["match",["get","class"],["motorway","trunk","primary"],1.4,0.4],14,["match",["get","class"],["motorway","trunk","primary"],7,["secondary","tertiary"],5,2.5]]}}""",
            """{"id":"rail","type":"line","source":"omt","source-layer":"transportation","filter":["==",["get","class"],"rail"],"paint":{"line-color":"#9a9a9a","line-width":1,"line-dasharray":[2,2]}}""",
        ) + if (!glyphs) emptyList() else listOf(
            """{"id":"water-name","type":"symbol","source":"omt","source-layer":"water_name","layout":{"text-field":$name,"text-font":["Noto Sans Regular"],"text-size":12},"paint":{"text-color":"#4a7bb0","text-halo-color":"#ffffff","text-halo-width":1}}""",
            """{"id":"road-name","type":"symbol","source":"omt","source-layer":"transportation_name","minzoom":12,"layout":{"symbol-placement":"line","text-field":$name,"text-font":["Noto Sans Regular"],"text-size":11},"paint":{"text-color":"#555555","text-halo-color":"#ffffff","text-halo-width":1.2}}""",
            """{"id":"place-name","type":"symbol","source":"omt","source-layer":"place","layout":{"text-field":$name,"text-font":["Noto Sans Medium"],"text-size":["match",["get","class"],"city",17,"town",14,"village",12,11],"text-max-width":8},"paint":{"text-color":"#333333","text-halo-color":"#ffffff","text-halo-width":1.5}}""",
        )
        val overlay = listOf(
            """{"id":"places-dot","type":"circle","source":"places","paint":{"circle-radius":["interpolate",["linear"],["zoom"],8,4,14,9],"circle-color":["match",["get","kind"],"cyclone_shelter","#2e7d32","flood_shelter","#00897b","hospital","#1565c0","police","#283593","#6d4c41"],"circle-stroke-width":2,"circle-stroke-color":"#ffffff"}}""",
            """{"id":"sos-dot","type":"circle","source":"sos","paint":{"circle-radius":12,"circle-color":"#d32f2f","circle-stroke-width":3,"circle-stroke-color":"#ffffff"}}""",
            """{"id":"me-dot","type":"circle","source":"me","paint":{"circle-radius":9,"circle-color":"#1e88e5","circle-stroke-width":3,"circle-stroke-color":"#ffffff"}}""",
        ) + if (!glyphs) emptyList() else listOf(
            """{"id":"places-name","type":"symbol","source":"places","minzoom":12,"layout":{"text-field":["get","name"],"text-font":["Noto Sans Medium"],"text-size":12,"text-offset":[0,1.2],"text-anchor":"top","text-max-width":9},"paint":{"text-color":"#1b5e20","text-halo-color":"#ffffff","text-halo-width":1.5}}""",
            """{"id":"sos-name","type":"symbol","source":"sos","layout":{"text-field":["get","name"],"text-font":["Noto Sans Medium"],"text-size":13,"text-offset":[0,1.4],"text-anchor":"top"},"paint":{"text-color":"#b71c1c","text-halo-color":"#ffffff","text-halo-width":1.5}}""",
        )
        val sources = buildString {
            if (pmtiles != null) append("\"omt\":{\"type\":\"vector\",\"url\":\"pmtiles://file://${pmtiles.absolutePath}\",\"attribution\":\"© OpenStreetMap contributors\"},")
            append("\"places\":{\"type\":\"geojson\",\"data\":$EMPTY},")
            append("\"sos\":{\"type\":\"geojson\",\"data\":$EMPTY},")
            append("\"me\":{\"type\":\"geojson\",\"data\":$EMPTY}")
        }
        val glyphsLine = if (glyphs) "\"glyphs\":\"asset://map/glyphs/{fontstack}/{range}.pbf\"," else ""
        val bg = """{"id":"bg","type":"background","paint":{"background-color":"#f2efe9"}}"""
        return """{"version":8,"name":"rakshakmesh",$glyphsLine"center":[$centerLon,$centerLat],"zoom":11,"sources":{$sources},"layers":[${(listOf(bg) + base + overlay).joinToString(",")}]}"""
    }

    private fun fill(id: String, layer: String, color: String, opacity: Double) =
        """{"id":"$id","type":"fill","source":"omt","source-layer":"$layer","paint":{"fill-color":"$color","fill-opacity":$opacity}}"""

    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun point(lon: Double, lat: Double, props: Map<String, String>): JsonObject = buildJsonObject {
        put("type", "Feature")
        putJsonObject("geometry") {
            put("type", "Point")
            putJsonArray("coordinates") { add(JsonPrimitive(lon)); add(JsonPrimitive(lat)) }
        }
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
    }

    fun collection(features: List<JsonObject>): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }.toString()
}

/** Camera request: move to this point. [seq] changes every time the user taps "my location". */
data class CameraTarget(val lat: Double, val lon: Double, val zoom: Double, val seq: Int)

@Composable
fun OfflineMap(
    pmtiles: File?,
    startLat: Double,
    startLon: Double,
    places: String,
    sos: String,
    me: String,
    camera: CameraTarget?,
    onFeatureClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val click by rememberUpdatedState(onFeatureClick)
    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, pmtiles) {
        val glyphs = MapAssets.hasGlyphs(ctx)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setMinZoomPreference(5.0)
            m.setMaxZoomPreference(17.0)
            m.setStyle(Style.Builder().fromJson(MapAssets.style(pmtiles, glyphs, startLat, startLon))) { s -> style = s }
            m.addOnMapClickListener { latLng ->
                val pt = m.projection.toScreenLocation(latLng)
                val f = m.queryRenderedFeatures(pt, "sos-dot", "places-dot").firstOrNull()
                val id = f?.getStringProperty("id")
                if (id != null) click(id)
                id != null
            }
        }
    }

    LaunchedEffect(style, places) { style?.getSourceAs<GeoJsonSource>("places")?.setGeoJson(places) }
    LaunchedEffect(style, sos) { style?.getSourceAs<GeoJsonSource>("sos")?.setGeoJson(sos) }
    LaunchedEffect(style, me) { style?.getSourceAs<GeoJsonSource>("me")?.setGeoJson(me) }
    LaunchedEffect(map, camera) {
        val m = map ?: return@LaunchedEffect
        val c = camera ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), c.zoom), 600)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
