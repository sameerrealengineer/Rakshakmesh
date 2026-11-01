package org.rakshakmesh.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.rakshakmesh.core.Fix
import org.rakshakmesh.core.ShelterRecord
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline map from a local PMTiles file (OpenStreetMap data, OpenMapTiles schema, built with
 * Planetiler). We never download tiles from tile.openstreetmap.org (OSMF policy, PRD 5.3).
 * No text labels: labels need font files; shelter names are in the list below the map.
 */
object OfflineMapFile {
    fun file(context: Context): File = File(context.filesDir, "maps/region.pmtiles")
    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    /** One-time download from our own storage. Reports progress 0..100. */
    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val target = file(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            if (!tmp.renameTo(target)) error("rename failed")
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    /** Copy a .pmtiles file the user picked (side-loaded by the NGO, Bluetooth, SD card). */
    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val target = file(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: error("cannot open file")
        if (!tmp.renameTo(target)) error("rename failed")
    }

    fun styleJson(context: Context, center: Fix?, shelters: List<ShelterRecord>): String {
        val path = file(context).absolutePath
        val lon = center?.lon ?: shelters.firstOrNull()?.lon ?: 85.83
        val lat = center?.lat ?: shelters.firstOrNull()?.lat ?: 20.30
        fun point(lo: Double, la: Double, props: Map<String, String> = emptyMap()) = buildJsonObject {
            put("type", "Feature")
            putJsonObject("geometry") {
                put("type", "Point")
                putJsonArray("coordinates") { add(JsonPrimitive(lo)); add(JsonPrimitive(la)) }
            }
            putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        }
        fun collection(features: List<kotlinx.serialization.json.JsonObject>) = buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }
        fun layer(id: String, type: String, source: String, sourceLayer: String?, paint: String, extra: String = "") =
            """{"id":"$id","type":"$type","source":"$source"${sourceLayer?.let { ",\"source-layer\":\"$it\"" } ?: ""}$extra,"paint":$paint}"""

        val shelterFc = collection(shelters.map { point(it.lon, it.lat, mapOf("name" to it.name)) })
        val meFc = collection(listOfNotNull(center?.let { point(it.lon, it.lat) }))
        val layers = listOf(
            """{"id":"bg","type":"background","paint":{"background-color":"#f2efe9"}}""",
            layer("landcover", "fill", "omt", "landcover", """{"fill-color":"#dfe9d0","fill-opacity":0.6}"""),
            layer("water", "fill", "omt", "water", """{"fill-color":"#9fc8ef"}"""),
            layer("waterway", "line", "omt", "waterway", """{"line-color":"#9fc8ef","line-width":1.5}"""),
            layer("building", "fill", "omt", "building", """{"fill-color":"#d9d0c9"}""", ""","minzoom":14"""),
            layer("road-casing", "line", "omt", "transportation", """{"line-color":"#b5aca3","line-width":["interpolate",["linear"],["zoom"],8,1,16,9]}"""),
            layer("road", "line", "omt", "transportation", """{"line-color":"#ffffff","line-width":["interpolate",["linear"],["zoom"],8,0.5,16,7]}"""),
            layer("boundary", "line", "omt", "boundary", """{"line-color":"#9e7ea8","line-width":1,"line-dasharray":[3,2]}"""),
            layer("shelters", "circle", "shelters", null, """{"circle-radius":9,"circle-color":"#2e7d32","circle-stroke-width":3,"circle-stroke-color":"#ffffff"}"""),
            layer("me", "circle", "me", null, """{"circle-radius":8,"circle-color":"#1565c0","circle-stroke-width":3,"circle-stroke-color":"#ffffff"}"""),
        )
        return """
            {"version":8,"name":"rakshakmesh-offline","center":[$lon,$lat],"zoom":12,
             "sources":{
               "omt":{"type":"vector","url":"pmtiles://file://$path","attribution":"© OpenStreetMap contributors"},
               "shelters":{"type":"geojson","data":$shelterFc},
               "me":{"type":"geojson","data":$meFc}
             },
             "layers":[${layers.joinToString(",")}]}
        """.trimIndent()
    }
}

@Composable
fun OfflineMap(center: Fix?, shelters: List<ShelterRecord>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(Bundle()) }
    }
    val style = remember(center, shelters) { OfflineMapFile.styleJson(ctx, center, shelters) }

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

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view -> view.getMapAsync { map -> map.setStyle(Style.Builder().fromJson(style)) } },
    )
}
