package org.rakshakmesh.core

import org.rakshakmesh.core.mesh.MeshCodec
import org.rakshakmesh.core.mesh.MeshPacket
import org.rakshakmesh.core.mesh.MeshRouter
import org.rakshakmesh.core.mesh.MeshRules
import org.rakshakmesh.core.mesh.PacketType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshTest {
    private val now = 1_800_000_000_000L
    private fun sos() = MeshPacket.new(PacketType.SOS, "phoneA", "Rina", now, body = "SOS RM1 TRAPPED", lat = 19.8, lon = 85.8)

    @Test fun duplicatesAndExpiredRejected() {
        val r = MeshRouter()
        val p = sos()
        assertEquals(MeshRouter.Verdict.ACCEPT, r.receive(p, now))
        assertEquals(MeshRouter.Verdict.DUPLICATE, r.receive(p.forwarded(), now))
        assertEquals(MeshRouter.Verdict.EXPIRED, r.receive(sos(), now + MeshRules.DEFAULT_TTL_MS + 1))
    }

    @Test fun hopLimit() {
        val r = MeshRouter()
        var p = sos()
        repeat(MeshRules.DEFAULT_MAX_HOPS) {
            assertTrue(r.shouldForward(p, now), "hop ${p.hops}")
            p = p.forwarded()
        }
        assertFalse(r.shouldForward(p, now))
    }

    @Test fun mediaRules() {
        val r = MeshRouter()
        val video = MeshPacket.new(PacketType.VIDEO, "a", "A", now).copy(size = 5_000_000, fileName = "v.mp4")
        assertTrue(r.shouldForward(video, now))
        assertFalse(r.shouldForward(video.forwarded(), now))
        val voiceSmall = MeshPacket.new(PacketType.VOICE, "a", "A", now).copy(size = 200_000)
        assertTrue(r.shouldForward(voiceSmall.forwarded().forwarded(), now))
        val voiceBig = voiceSmall.copy(size = 3_000_000)
        assertFalse(r.shouldForward(voiceBig, now))
        assertFalse(r.shouldForward(MeshPacket.new(PacketType.HELLO, "a", "A", now), now))
    }

    @Test fun invalidPacketsRejected() {
        val r = MeshRouter()
        assertEquals(MeshRouter.Verdict.INVALID, r.receive(sos().copy(lat = 120.0), now))
        assertEquals(MeshRouter.Verdict.INVALID, r.receive(MeshPacket.new(PacketType.IMAGE, "a", "A", now), now)) // no size
        val tooBig = MeshPacket.new(PacketType.VIDEO, "a", "A", now).copy(size = MeshRules.MAX_VIDEO_BYTES + 1)
        assertEquals(MeshRouter.Verdict.INVALID, r.receive(tooBig, now))
        assertEquals(MeshRouter.Verdict.INVALID, r.receive(sos().copy(body = "x".repeat(5000)), now))
    }

    @Test fun codecRoundTripAndSmallHeader() {
        val p = sos().copy(filePayloadId = 42L)
        val bytes = MeshCodec.encode(p)
        assertTrue(bytes.size < MeshRules.MAX_HEADER_BYTES)
        assertEquals(p, MeshCodec.decode(bytes))
        assertNull(MeshCodec.decode("not json".toByteArray()))
        assertNull(p.forwarded().filePayloadId)
        assertNotNull(MeshCodec.sha256(byteArrayOf(1, 2, 3)))
        assertEquals(64, MeshCodec.sha256(byteArrayOf()).length)
    }
}
