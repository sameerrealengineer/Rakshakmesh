package org.rakshakmesh.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.Fix
import org.rakshakmesh.core.Geo
import org.rakshakmesh.core.mesh.MeshRules
import org.rakshakmesh.core.mesh.PacketType
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.data.MeshMessage
import org.rakshakmesh.device.Phone
import org.rakshakmesh.mesh.MeshService
import org.rakshakmesh.mesh.MeshState
import org.rakshakmesh.mesh.Permissions
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.directionLabel
import org.rakshakmesh.ui.formatDateTime
import org.rakshakmesh.ui.km
import org.rakshakmesh.ui.theme.SafeGreen
import org.rakshakmesh.ui.theme.SosRed
import java.io.File
import java.util.UUID

/**
 * Offline help from nearby phones: no internet, no SIM, no tower. Finds RakshakMesh phones over
 * Bluetooth / Wi-Fi automatically, shares SOS, text, voice notes, photos and videos, and passes
 * messages on to phones further away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(app: RakshakApp, settings: AppSettings, onShowMap: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.mesh.state.collectAsStateWithLifecycle()
    val peers by app.mesh.peers.collectAsStateWithLifecycle()
    val feed by app.db.mesh().observeFeed().collectAsStateWithLifecycle(emptyList())
    var me by remember { mutableStateOf<Fix?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    val player = remember { VoicePlayer() }
    DisposableEffect(Unit) { onDispose { recorder.cancel(); player.stop() } }
    LaunchedEffect(Unit) { me = app.location.lastKnown() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Permissions.hasMesh(ctx)) scope.launch {
            app.settings.setMeshEnabled(true)
            MeshService.start(ctx)
            app.mesh.start()
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun sendResult(n: Int) {
        note = if (n > 0) ctx.getString(R.string.mesh_sent_to, n) else ctx.getString(R.string.mesh_sent_later)
    }

    fun sendFile(type: PacketType, file: File, mime: String) = scope.launch {
        if (file.length() > MeshRules.maxFileBytes(type)) {
            note = ctx.getString(R.string.mesh_file_too_big, (MeshRules.maxFileBytes(type) / 1024 / 1024).toInt())
            file.delete()
            return@launch
        }
        sendResult(app.mesh.sendOwn(type, body = "", lat = me?.lat, lon = me?.lon, file = file, mime = mime))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val isVideo = mime.startsWith("video")
            val file = withContext(Dispatchers.IO) { if (isVideo) copyToMesh(ctx, uri, "mp4") else compressImage(ctx, uri) }
            if (file == null) note = ctx.getString(R.string.mesh_file_error)
            else sendFile(if (isVideo) PacketType.VIDEO else PacketType.IMAGE, file, if (isVideo) mime else "image/jpeg")
        }
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) scope.launch {
            val out = withContext(Dispatchers.IO) { compressImage(ctx, Uri.fromFile(f)) }
            f.delete()
            out?.let { sendFile(PacketType.IMAGE, it, "image/jpeg") }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title = { Text(stringResource(R.string.nearby_title)) })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                MeshStatusCard(
                    state, peers.values.map { it.name }, settings.meshEnabled,
                    onEnable = { permLauncher.launch(Permissions.all()) },
                    onOpenLocationSettings = { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val fix = app.location.currentFix(10_000) ?: app.location.lastKnown()
                            me = fix ?: me
                            val p = app.db.profile().get()
                            val where = listOfNotNull(p?.landmark, p?.village, p?.district).filter { it.isNotBlank() }.joinToString(", ")
                            val body = ctx.getString(R.string.mesh_sos_body, where.ifBlank { "-" })
                            sendResult(app.mesh.sendOwn(PacketType.SOS, body, fix?.lat, fix?.lon, fix?.accuracyM))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("mesh_sos"),
                ) {
                    Icon(Icons.Filled.Campaign, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mesh_sos_all), style = MaterialTheme.typography.titleMedium)
                }
            }
            note?.let { item { InfoCard(it) } }
            if (feed.isEmpty()) {
                item { InfoCard(stringResource(R.string.mesh_empty)) }
            }
            items(feed, key = { it.id }) { m ->
                MessageCard(m, me, player, onShowMap = onShowMap, onForward = {
                    scope.launch {
                        val to = app.outbox.recipients()
                        val body = m.body + (if (m.lat != null) " | maps.google.com/?q=${m.lat},${m.lon}" else "")
                        if (!Phone.composeSms(ctx, to, body.take(300))) note = ctx.getString(R.string.sos_no_sms_app)
                    }
                })
            }
        }

        // Composer.
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.mesh_attach))
            }
            IconButton(onClick = {
                val dir = File(ctx.cacheDir, "capture").apply { mkdirs() }
                val f = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                cameraFile = f
                runCatching { camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)) }
            }) { Icon(Icons.Filled.PhotoCamera, stringResource(R.string.mesh_camera)) }
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(MeshRules.MAX_BODY_CHARS) },
                placeholder = { Text(if (recording) stringResource(R.string.mesh_recording) else stringResource(R.string.mesh_type_here)) },
                modifier = Modifier.weight(1f).testTag("mesh_input"), maxLines = 3,
            )
            if (text.isNotBlank()) {
                FilledIconButton(onClick = {
                    val t = text.trim()
                    text = ""
                    scope.launch { sendResult(app.mesh.sendOwn(PacketType.TEXT, t, me?.lat, me?.lon)) }
                }, modifier = Modifier.testTag("mesh_send")) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.mesh_send)) }
            } else {
                // Hold to record a voice note, release to send.
                Surface(
                    shape = CircleShape,
                    color = if (recording) SosRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (!Permissions.hasMic(ctx)) { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                            recording = recorder.start()
                            val released = tryAwaitRelease()
                            if (recording) {
                                recording = false
                                val f = recorder.stop()
                                if (released && f != null) sendFile(PacketType.VOICE, f, "audio/mp4")
                                else if (f == null) note = ctx.getString(R.string.mesh_voice_short)
                            }
                        })
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, stringResource(R.string.mesh_hold_to_talk), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeshStatusCard(
    state: MeshState,
    peerNames: List<String>,
    enabled: Boolean,
    onEnable: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val on = state == MeshState.ON
    InfoCard(
        when {
            state == MeshState.UNSUPPORTED -> stringResource(R.string.mesh_unsupported)
            state == MeshState.NO_PERMISSION || !enabled || state == MeshState.OFF -> stringResource(R.string.mesh_off_explain)
            state == MeshState.ERROR -> stringResource(R.string.mesh_error)
            state == MeshState.STARTING -> stringResource(R.string.mesh_starting)
            peerNames.isEmpty() -> stringResource(R.string.mesh_searching)
            else -> stringResource(R.string.mesh_connected, peerNames.size, peerNames.joinToString(", "))
        },
        color = if (on && peerNames.isNotEmpty()) SafeGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(stringResource(R.string.mesh_how), style = MaterialTheme.typography.bodyMedium)
        if (!on && state != MeshState.UNSUPPORTED) {
            Button(onClick = onEnable, modifier = Modifier.testTag("mesh_enable")) { Text(stringResource(R.string.mesh_turn_on)) }
        }
        if (Permissions.locationServicesNeeded(ctx)) {
            Text(stringResource(R.string.mesh_location_needed), color = SosRed, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenLocationSettings) { Text(stringResource(R.string.mesh_open_location)) }
        }
    }
}

@Composable
private fun MessageCard(m: MeshMessage, me: Fix?, player: VoicePlayer, onShowMap: () -> Unit, onForward: () -> Unit) {
    val ctx = LocalContext.current
    val isSos = m.type == "SOS"
    val color = when {
        isSos -> SosRed.copy(alpha = 0.15f)
        m.type == "SAFE" -> SafeGreen.copy(alpha = 0.15f)
        m.outgoing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val who = if (m.outgoing) stringResource(R.string.mesh_me) else m.originName.ifBlank { "?" }
            val kind = when (m.type) {
                "SOS" -> stringResource(R.string.mesh_kind_sos)
                "SAFE" -> stringResource(R.string.mesh_kind_safe)
                "ALERT" -> stringResource(R.string.mesh_kind_alert)
                "VOICE" -> stringResource(R.string.mesh_kind_voice)
                "IMAGE" -> stringResource(R.string.mesh_kind_photo)
                "VIDEO" -> stringResource(R.string.mesh_kind_video)
                else -> stringResource(R.string.mesh_kind_text)
            }
            Text("$kind · $who", fontWeight = FontWeight.Bold, color = if (isSos) SosRed else MaterialTheme.colorScheme.onSurface)
            val meta = buildList {
                add(formatDateTime(m.createdAt))
                if (!m.outgoing && m.hops > 0) add(ctx.getString(R.string.mesh_hops, m.hops))
                if (m.outgoing) add(if (m.sharedWith > 0) ctx.getString(R.string.mesh_shared_n, m.sharedWith) else ctx.getString(R.string.mesh_waiting))
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodyMedium)
            if (m.lat != null && m.lon != null && me != null && !m.outgoing) {
                val d = Geo.distanceKm(me.lat, me.lon, m.lat, m.lon)
                val dir = Geo.compass(Geo.bearingDeg(me.lat, me.lon, m.lat, m.lon))
                Text(stringResource(R.string.mesh_distance, km(d), directionLabel(dir)), fontWeight = FontWeight.SemiBold)
            }
            if (m.type == "ALERT") {
                Text(alertHeadline(m.body), style = MaterialTheme.typography.bodyLarge)
            } else if (m.body.isNotBlank()) {
                Text(m.body, style = MaterialTheme.typography.bodyLarge)
            }
            val path = m.localPath
            when (m.type) {
                "VOICE" -> if (path != null) OutlinedButton(onClick = { player.toggle(path) }) {
                    Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.mesh_play))
                }
                "IMAGE" -> if (path != null) Thumbnail(path) { openFile(ctx, path, "image/*") }
                "VIDEO" -> if (path != null) OutlinedButton(onClick = { openFile(ctx, path, m.mime ?: "video/*") }) {
                    Icon(Icons.Filled.Videocam, null); Text(stringResource(R.string.mesh_play_video))
                }
            }
            if (isSos && !m.outgoing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.lat != null) OutlinedButton(onClick = onShowMap) { Text(stringResource(R.string.mesh_show_map)) }
                    OutlinedButton(onClick = onForward) { Text(stringResource(R.string.notif_forward_sms)) }
                    OutlinedButton(onClick = { Phone.dial(ctx, "112") }) { Text("112") }
                }
            }
        }
    }
}

private fun alertHeadline(body: String): String =
    Regex("\"headline\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)?.replace("\\\"", "\"") ?: body.take(200)

@Composable
private fun Thumbnail(path: String, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                val scale = maxOf(1, maxOf(o.outWidth, o.outHeight) / 600)
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = scale })
            }.getOrNull()
        }
    }
    bmp?.let {
        Image(
            it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clickable(onClick = onClick),
        )
    }
}

private fun openFile(ctx: Context, path: String, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun meshOutDir(ctx: Context) = File(ctx.filesDir, "mesh").apply { mkdirs() }

private fun copyToMesh(ctx: Context, uri: Uri, ext: String): File? = runCatching {
    val out = File(meshOutDir(ctx), "out_${UUID.randomUUID()}.$ext")
    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: return null
    out
}.getOrNull()

/** Photos are shrunk to about 1600 px so they cross Bluetooth quickly. */
private fun compressImage(ctx: Context, uri: Uri): File? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val scale = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1600)
    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = scale })
    } ?: return null
    val out = File(meshOutDir(ctx), "out_${UUID.randomUUID()}.jpg")
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    bmp.recycle()
    out
}.getOrNull()

/** Small AAC voice notes (~250 KB per minute), capped at 2 minutes. */
private class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean = runCatching {
        val f = File(meshOutDir(ctx), "voice_${UUID.randomUUID()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(32_000)
        r.setMaxDuration(120_000)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis()
        true
    }.getOrElse { false }

    /** Returns the file, or null if it was too short or failed. */
    fun stop(): File? {
        val r = rec ?: return null
        val f = file
        rec = null; file = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || System.currentTimeMillis() - startedAt < 700 || f == null || f.length() == 0L) {
            f?.delete(); return null
        }
        return f
    }

    fun cancel() { stop()?.delete() }
}

private class VoicePlayer {
    private var mp: MediaPlayer? = null
    private var current: String? = null

    fun toggle(path: String) {
        val same = current == path
        stop()
        if (same) return
        runCatching {
            mp = MediaPlayer().apply {
                setDataSource(path); prepare(); start()
                setOnCompletionListener { stop() }
            }
            current = path
        }
    }

    fun stop() {
        mp?.release(); mp = null; current = null
    }
}
