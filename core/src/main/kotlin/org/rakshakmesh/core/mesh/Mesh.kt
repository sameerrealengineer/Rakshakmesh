package org.rakshakmesh.core.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/**
 * Offline phone-to-phone messages over Bluetooth / Wi-Fi (Nearby Connections).
 * No internet, no SIM, no tower needed. Each phone stores packets and passes them on to
 * other phones it meets, so a message can travel several hops (store-and-forward).
 */
enum class PacketType { SOS, SAFE, TEXT, VOICE, IMAGE, VIDEO, ALERT, HELLO }

@Serializable
data class MeshPacket(
    val id: String,
    val type: PacketType,
    val originId: String,
    val originName: String,
    val createdAt: Long,
    val expiresAt: Long,
    val hops: Int = 0,
    val maxHops: Int = MeshRules.DEFAULT_MAX_HOPS,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val body: String = "",
    val fileName: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val sha256: String? = null,
    /** Payload id of the FILE that follows this header (media packets only). */
    val filePayloadId: Long? = null,
    val schema: Int = 1,
) {
    val hasFile: Boolean get() = type == PacketType.VOICE || type == PacketType.IMAGE || type == PacketType.VIDEO

    /** The copy sent onward: one more hop, and no stale payload id from the previous link. */
    fun forwarded(): MeshPacket = copy(hops = hops + 1, filePayloadId = null)

    companion object {
        fun new(
            type: PacketType,
            originId: String,
            originName: String,
            now: Long,
            body: String = "",
            lat: Double? = null,
            lon: Double? = null,
            accuracyM: Float? = null,
            ttlMillis: Long = MeshRules.DEFAULT_TTL_MS,
        ) = MeshPacket(
            id = UUID.randomUUID().toString(), type = type, originId = originId, originName = originName,
            createdAt = now, expiresAt = now + ttlMillis, body = body, lat = lat, lon = lon, accuracyM = accuracyM,
        )
    }
}

object MeshRules {
    const val DEFAULT_MAX_HOPS = 6
    const val DEFAULT_TTL_MS = 24L * 3600_000
    const val MAX_VOICE_RELAY_BYTES = 1L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 50L * 1024 * 1024
    const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
    const val MAX_BODY_CHARS = 2000
    /** Nearby Connections limit for a BYTES payload is 32 KB; keep headers far below it. */
    const val MAX_HEADER_BYTES = 30 * 1024

    fun maxFileBytes(type: PacketType): Long = when (type) {
        PacketType.VIDEO -> MAX_VIDEO_BYTES
        PacketType.IMAGE -> MAX_IMAGE_BYTES
        PacketType.VOICE -> 10L * 1024 * 1024
        else -> 0
    }
}

/** Decides what to keep and what to pass on. Pure logic, unit-tested. */
class MeshRouter(private val seen: MutableSet<String> = HashSet()) {

    enum class Verdict { ACCEPT, DUPLICATE, EXPIRED, INVALID }

    fun markSeen(id: String) { seen += id }
    fun hasSeen(id: String) = id in seen

    /** Check an incoming packet. ACCEPT means: store it, show it, and consider forwarding. */
    fun receive(p: MeshPacket, now: Long): Verdict {
        if (!isValid(p)) return Verdict.INVALID
        if (p.expiresAt <= now) return Verdict.EXPIRED
        if (!seen.add(p.id)) return Verdict.DUPLICATE
        return Verdict.ACCEPT
    }

    /** Whether this phone should pass [p] on to other phones. */
    fun shouldForward(p: MeshPacket, now: Long): Boolean {
        if (p.type == PacketType.HELLO) return false
        if (p.expiresAt <= now) return false
        if (p.hops >= p.maxHops) return false
        return when (p.type) {
            PacketType.SOS, PacketType.SAFE, PacketType.TEXT, PacketType.ALERT -> true
            // Short voice notes travel far; big files only go to direct neighbours.
            PacketType.VOICE -> (p.size ?: Long.MAX_VALUE) <= MeshRules.MAX_VOICE_RELAY_BYTES
            PacketType.IMAGE, PacketType.VIDEO -> p.hops == 0
            PacketType.HELLO -> false
        }
    }

    private fun isValid(p: MeshPacket): Boolean {
        if (p.id.isBlank() || p.originId.isBlank()) return false
        if (p.hops < 0 || p.maxHops > 20) return false
        if (p.body.length > MeshRules.MAX_BODY_CHARS) return false
        if (p.lat != null && p.lat !in -90.0..90.0) return false
        if (p.lon != null && p.lon !in -180.0..180.0) return false
        if (p.hasFile) {
            val size = p.size ?: return false
            if (size <= 0 || size > MeshRules.maxFileBytes(p.type)) return false
        }
        return true
    }
}

object MeshCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(p: MeshPacket): ByteArray = json.encodeToString(MeshPacket.serializer(), p).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): MeshPacket? =
        runCatching { json.decodeFromString(MeshPacket.serializer(), bytes.toString(Charsets.UTF_8)) }.getOrNull()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
