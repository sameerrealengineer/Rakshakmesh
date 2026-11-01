package org.rakshakmesh.mesh

import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.Geo
import org.rakshakmesh.core.mesh.MeshCodec
import org.rakshakmesh.core.mesh.MeshPacket
import org.rakshakmesh.core.mesh.MeshRouter
import org.rakshakmesh.core.mesh.MeshRules
import org.rakshakmesh.core.mesh.PacketType
import org.rakshakmesh.data.AlertCache
import org.rakshakmesh.data.MeshMessage
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Official alert carried over the mesh so phones without internet still get it. */
@Serializable
data class AlertGossip(
    val alertId: String,
    val district: String,
    val level: String,
    val headline: String,
    val source: String,
    val agency: String? = null,
    val issuedAt: Long? = null,
    val validUntil: Long? = null,
    val originalText: String = "",
)

data class Peer(val endpointId: String, val name: String, val connectedAt: Long)

enum class MeshState { OFF, STARTING, ON, NO_PERMISSION, UNSUPPORTED, ERROR }

/**
 * Offline phone-to-phone network using Google Nearby Connections (Bluetooth, BLE and Wi-Fi
 * underneath, chosen automatically). Every phone advertises and discovers at the same time,
 * connects automatically to any nearby RakshakMesh phone, and passes messages on
 * (store-and-forward), so help requests travel further than one Bluetooth range.
 * Works with mobile data off, no SIM and no tower.
 */
class MeshManager(private val app: RakshakApp) {
    companion object {
        const val SERVICE_ID = "org.rakshakmesh.mesh.v1"
        private const val TAG = "RakshakMesh"
        private const val MAX_PEERS = 8
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val _state = MutableStateFlow(MeshState.OFF)
    val state: StateFlow<MeshState> = _state
    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers

    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(app) }
    private val router = MeshRouter(ConcurrentHashMap.newKeySet())
    private val endpointNames = ConcurrentHashMap<String, String>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val headersByPayload = ConcurrentHashMap<Long, Pair<String, MeshPacket>>()
    private val filesByPayload = ConcurrentHashMap<Long, Payload>()
    private val finishedFiles = ConcurrentHashMap.newKeySet<Long>()
    private var myId = ""
    private var myName = ""
    private var refreshJob: Job? = null

    val isSupported: Boolean by lazy {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
    }

    // ---------------------------------------------------------------- start / stop

    suspend fun start() {
        if (_state.value == MeshState.ON || _state.value == MeshState.STARTING) return
        if (!isSupported) { _state.value = MeshState.UNSUPPORTED; return }
        if (!Permissions.hasMesh(app)) { _state.value = MeshState.NO_PERMISSION; return }
        _state.value = MeshState.STARTING
        myId = app.settings.deviceId()
        val name = app.db.profile().get()?.name?.takeIf { it.isNotBlank() } ?: "RakshakMesh"
        myName = "${name.take(24).replace('|', ' ')}|$myId"
        withContext(Dispatchers.IO) { app.db.mesh().allIds().forEach(router::markSeen) }
        startRadios()
        refreshJob?.cancel()
        // Re-scan every 45 s so phones that walk into range later are found too.
        refreshJob = app.appScope.launch {
            while (isActive) {
                delay(45_000)
                if (_state.value == MeshState.ON && _peers.value.size < MAX_PEERS) {
                    client.stopDiscovery()
                    startDiscovery()
                }
                app.db.mesh().prune(System.currentTimeMillis() - 7L * 24 * 3600_000)
            }
        }
    }

    private fun startRadios() {
        val adv = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(myName, SERVICE_ID, lifecycle, adv)
            .addOnSuccessListener { _state.value = MeshState.ON }
            .addOnFailureListener { e ->
                if ((e as? com.google.android.gms.common.api.ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    _state.value = MeshState.ON
                } else {
                    Log.w(TAG, "advertising failed", e)
                    _state.value = MeshState.ERROR
                }
            }
        startDiscovery()
    }

    private fun startDiscovery() {
        val opts = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startDiscovery(SERVICE_ID, discovery, opts)
            .addOnFailureListener { e -> Log.w(TAG, "discovery failed", e) }
    }

    fun stop() {
        refreshJob?.cancel()
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        }
        connecting.clear()
        _peers.value = emptyMap()
        _state.value = MeshState.OFF
    }

    // ---------------------------------------------------------------- connections

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            endpointNames[endpointId] = info.endpointName
            connectTo(endpointId, attempt = 0)
        }

        override fun onEndpointLost(endpointId: String) {
            connecting.remove(endpointId)
        }
    }

    private fun connectTo(endpointId: String, attempt: Int) {
        if (_peers.value.containsKey(endpointId) || _peers.value.size >= MAX_PEERS) return
        if (!connecting.add(endpointId)) return
        client.requestConnection(myName, endpointId, lifecycle)
            .addOnFailureListener { e ->
                connecting.remove(endpointId)
                val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                if (attempt < 3 && _state.value == MeshState.ON) {
                    app.appScope.launch {
                        delay(2_000L * (attempt + 1) + (0..1500).random())
                        connectTo(endpointId, attempt + 1)
                    }
                }
            }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointNames[endpointId] = info.endpointName
            // Emergency use: accept automatically. The link itself is encrypted by Nearby.
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connecting.remove(endpointId)
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val name = displayName(endpointNames[endpointId])
                _peers.value = _peers.value + (endpointId to Peer(endpointId, name, System.currentTimeMillis()))
                MeshService.refreshNotification(app)
                app.appScope.launch { syncTo(endpointId) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            _peers.value = _peers.value - endpointId
            connecting.remove(endpointId)
            MeshService.refreshNotification(app)
        }
    }

    private fun displayName(endpointName: String?) = endpointName?.substringBefore('|')?.ifBlank { null } ?: "RakshakMesh"

    /** A new neighbour: hand over everything still useful that it may not have. */
    private suspend fun syncTo(endpointId: String) {
        val now = System.currentTimeMillis()
        gossipAlerts()
        val items = withContext(Dispatchers.IO) { app.db.mesh().syncable(now) }
        for (m in items.reversed()) {
            val p = m.toPacket()
            if (!router.shouldForward(p, now) && !m.outgoing) continue
            val toSend = if (m.outgoing && p.hops == 0) p else p.forwarded()
            val file = m.localPath?.let(::File)?.takeIf { it.exists() }
            if (p.type == PacketType.VOICE && file == null) continue
            send(listOf(endpointId), toSend, file)
        }
    }

    // ---------------------------------------------------------------- receiving

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val p = payload.asBytes()?.let(MeshCodec::decode) ?: return
                    val fileId = p.filePayloadId
                    if (p.hasFile && fileId != null) {
                        headersByPayload[fileId] = endpointId to p
                        if (finishedFiles.remove(fileId)) app.appScope.launch { completeFile(fileId) }
                    } else {
                        app.appScope.launch { handle(p, endpointId, null) }
                    }
                }
                Payload.Type.FILE -> filesByPayload[payload.id] = payload
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val id = update.payloadId
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> if (filesByPayload.containsKey(id)) {
                    if (headersByPayload.containsKey(id)) app.appScope.launch { completeFile(id) } else finishedFiles.add(id)
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    filesByPayload.remove(id)?.let(::discard)
                    headersByPayload.remove(id)
                }
                else -> Unit
            }
        }
    }

    private suspend fun completeFile(payloadId: Long) {
        val payload = filesByPayload.remove(payloadId) ?: return
        val (from, header) = headersByPayload.remove(payloadId) ?: return
        val target = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.filesDir, "mesh").apply { mkdirs() }
                val ext = header.fileName?.substringAfterLast('.', "")?.take(5)?.ifBlank { null } ?: "bin"
                val out = File(dir, "${header.id}.$ext")
                val digest = MessageDigest.getInstance("SHA-256")
                val f = payload.asFile()
                val uri: Uri? = f?.asUri()
                val input = if (uri != null) app.contentResolver.openInputStream(uri) else {
                    @Suppress("DEPRECATION")
                    f?.asJavaFile()?.inputStream()
                }
                input?.use { ins ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            digest.update(buf, 0, n)
                            os.write(buf, 0, n)
                        }
                    }
                } ?: error("no input")
                discard(payload)
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (header.sha256 != null && header.sha256 != hash) {
                    out.delete()
                    null
                } else out
            }.getOrNull()
        }
        if (target != null) handle(header, from, target)
    }

    private fun discard(payload: Payload) {
        runCatching {
            payload.asFile()?.asUri()?.let { app.contentResolver.delete(it, null, null) }
        }
    }

    private suspend fun handle(p: MeshPacket, fromEndpoint: String, file: File?) {
        val now = System.currentTimeMillis()
        if (router.receive(p, now) != MeshRouter.Verdict.ACCEPT) return
        if (p.type == PacketType.HELLO) return
        val m = p.toMessage(outgoing = false, localPath = file?.absolutePath, now = now)
        withContext(Dispatchers.IO) { app.db.mesh().insert(m) }
        when (p.type) {
            PacketType.SOS -> Notifications.sos(app, m, distanceText(p))
            PacketType.ALERT -> storeGossipAlert(p)
            PacketType.SAFE, PacketType.TEXT, PacketType.VOICE, PacketType.IMAGE, PacketType.VIDEO -> Notifications.message(app, m)
            PacketType.HELLO -> Unit
        }
        if (router.shouldForward(p, now)) {
            val targets = _peers.value.keys.filter { it != fromEndpoint }
            if (targets.isNotEmpty()) send(targets, p.forwarded(), file)
        }
    }

    private suspend fun distanceText(p: MeshPacket): String? {
        val lat = p.lat ?: return null
        val lon = p.lon ?: return null
        val me = app.location.lastKnown() ?: return null
        val km = Geo.distanceKm(me.lat, me.lon, lat, lon)
        val dir = Geo.compass(Geo.bearingDeg(me.lat, me.lon, lat, lon))
        return String.format(java.util.Locale.US, "%.1f km %s", km, dir.name)
    }

    private suspend fun storeGossipAlert(p: MeshPacket) {
        val a = runCatching { json.decodeFromString(AlertGossip.serializer(), p.body) }.getOrNull() ?: return
        val myDistrict = app.db.profile().get()?.district ?: return
        if (!a.district.equals(myDistrict, true)) return
        withContext(Dispatchers.IO) {
            val existing = app.db.alerts().forDistrict(myDistrict).any { it.alertId == a.alertId }
            if (!existing) {
                app.db.alerts().upsertAll(
                    listOf(
                        AlertCache(
                            alertId = a.alertId, source = a.source, agency = a.agency, district = myDistrict,
                            level = a.level, headline = a.headline, originalText = a.originalText,
                            simplifiedOdia = null, issuedAt = a.issuedAt, validUntil = a.validUntil,
                            fetchedAt = p.createdAt, viaMesh = true,
                        ),
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------- sending

    /**
     * Send a message from this phone. It is stored first, so phones that come into range later
     * still receive it. Returns how many phones it was handed to right now.
     */
    suspend fun sendOwn(
        type: PacketType,
        body: String,
        lat: Double? = null,
        lon: Double? = null,
        accuracyM: Float? = null,
        file: File? = null,
        mime: String? = null,
    ): Int {
        val now = System.currentTimeMillis()
        if (myId.isBlank()) myId = app.settings.deviceId()
        val name = app.db.profile().get()?.name?.takeIf { it.isNotBlank() } ?: "RakshakMesh"
        var p = MeshPacket.new(type, myId, name, now, body = body.take(MeshRules.MAX_BODY_CHARS), lat = lat, lon = lon, accuracyM = accuracyM)
        if (file != null) {
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            p = p.copy(fileName = file.name, mime = mime, size = file.length(), sha256 = MeshCodec.sha256(bytes))
        }
        router.markSeen(p.id)
        withContext(Dispatchers.IO) { app.db.mesh().insert(p.toMessage(outgoing = true, localPath = file?.absolutePath, now = now)) }
        val targets = _peers.value.keys.toList()
        if (targets.isNotEmpty()) {
            send(targets, p, file)
            withContext(Dispatchers.IO) { app.db.mesh().addShared(p.id, targets.size) }
        }
        return targets.size
    }

    private fun send(targets: List<String>, p: MeshPacket, file: File?) {
        if (targets.isEmpty()) return
        try {
            if (file != null && p.hasFile) {
                val filePayload = Payload.fromFile(file)
                val header = p.copy(filePayloadId = filePayload.id)
                client.sendPayload(targets, Payload.fromBytes(MeshCodec.encode(header)))
                client.sendPayload(targets, filePayload)
            } else if (!p.hasFile) {
                client.sendPayload(targets, Payload.fromBytes(MeshCodec.encode(p)))
            }
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
        }
    }

    /** Share this phone's cached official alerts with neighbours (fixed ids, so no duplicates). */
    private suspend fun gossipAlerts() {
        val district = app.db.profile().get()?.district?.takeIf { it.isNotBlank() } ?: return
        val now = System.currentTimeMillis()
        val alerts = withContext(Dispatchers.IO) { app.db.alerts().forDistrict(district) }
            .filter { !it.viaMesh && (it.validUntil == null || it.validUntil > now) }
        for (a in alerts) {
            val id = "alert-${a.alertId}"
            if (router.hasSeen(id)) continue
            val body = json.encodeToString(
                AlertGossip.serializer(),
                AlertGossip(a.alertId, district, a.level, a.headline.take(300), a.source, a.agency, a.issuedAt, a.validUntil, a.originalText.take(1200)),
            )
            val p = MeshPacket(
                id = id, type = PacketType.ALERT, originId = myId, originName = myName.substringBefore('|'),
                createdAt = a.fetchedAt, expiresAt = a.validUntil ?: (now + MeshRules.DEFAULT_TTL_MS), body = body,
            )
            router.markSeen(id)
            withContext(Dispatchers.IO) { app.db.mesh().insert(p.toMessage(outgoing = true, localPath = null, now = now)) }
        }
    }
}

fun MeshPacket.toMessage(outgoing: Boolean, localPath: String?, now: Long) = MeshMessage(
    id = id, type = type.name, originId = originId, originName = originName, createdAt = createdAt,
    expiresAt = expiresAt, hops = hops, maxHops = maxHops, lat = lat, lon = lon, accuracyM = accuracyM,
    body = body, fileName = fileName, mime = mime, size = size, sha256 = sha256, localPath = localPath,
    outgoing = outgoing, receivedAt = now,
)

fun MeshMessage.toPacket() = MeshPacket(
    id = id, type = runCatching { PacketType.valueOf(type) }.getOrDefault(PacketType.TEXT), originId = originId,
    originName = originName, createdAt = createdAt, expiresAt = expiresAt, hops = hops, maxHops = maxHops,
    lat = lat, lon = lon, accuracyM = accuracyM, body = body, fileName = fileName, mime = mime, size = size, sha256 = sha256,
)
