package org.rakshakmesh.core

import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoTest {
    @Test fun distanceBhubaneswarToPuriIsAbout52km() {
        val d = Geo.distanceKm(20.2961, 85.8245, 19.8135, 85.8312)
        assertTrue(d in 52.0..55.0, "got $d")
    }

    @Test fun compassDirections() {
        assertEquals(Compass.N, Geo.compass(Geo.bearingDeg(20.0, 85.0, 20.1, 85.0)))
        assertEquals(Compass.E, Geo.compass(Geo.bearingDeg(20.0, 85.0, 20.0, 85.1)))
        assertEquals(Compass.NE, Geo.compass(Geo.bearingDeg(20.0, 85.0, 20.05, 85.05)))
        assertEquals(Compass.N, Geo.compass(359.0))
    }

    @Test fun coordFormatUsesDotAndFourDecimals() {
        assertEquals("20.2961", Geo.formatCoord(20.29614))
    }

    @Test fun districtSuggestion() {
        assertEquals("Puri", Districts.suggest(19.80, 85.82))
        assertEquals(30, Districts.ALL.size)
        assertEquals(Districts.ALL.toSet(), Districts.HQ.keys)
    }
}

class SmsBodyBuilderTest {
    private val now = OffsetDateTime.parse("2026-10-27T14:32:00+05:30").toInstant().toEpochMilli()
    private val fix = Fix(20.29612, 85.82451, 15f, now)

    @Test fun prdExampleFitsOneSms() {
        val s = SmsBodyBuilder.sos(
            SosKind.TRAPPED_BY_WATER, people = 4, elderly = 1,
            location = SosLocation.Fresh(fix),
            landmark = "Ward 5 near Shiv temple, Astaranga",
            details = SosDetails(bloodGroup = "B+"),
            nowMillis = now,
        )
        assertTrue(SmsBodyBuilder.isSingleSms(s), "${s.length}: $s")
        assertTrue(s.startsWith("SOS RM1 TRAPPED-WATER 4ppl 1eld | 20.2961,85.8245 +-15m 14:32"), s)
        assertTrue(s.contains("Astaranga"))
        assertTrue(s.contains("B+"))
    }

    @Test fun longLandmarkDropsLinkThenMedicalThenTrims() {
        val s = SmsBodyBuilder.sos(
            SosKind.INJURED_MEDICAL, 3, 0, SosLocation.Fresh(fix),
            landmark = "x".repeat(300),
            details = SosDetails("O-", cannotWalk = true, pregnant = true, infant = true),
            nowMillis = now,
        )
        assertEquals(SmsBodyBuilder.MAX_LEN, s.length)
        assertFalse(s.contains("maps.google"))
        assertFalse(s.contains("PREGNANT"))
        assertTrue(s.startsWith("SOS RM1 INJURED-MEDICAL 3ppl | 20.2961,85.8245"))
    }

    @Test fun mediumLandmarkKeepsMedicalDropsLink() {
        val s = SmsBodyBuilder.sos(
            SosKind.NEED_EVACUATION, 2, 0, SosLocation.Fresh(fix),
            landmark = "Behind the primary school, next to the big banyan tree, Ward 3",
            details = SosDetails("AB+", cannotWalk = true),
            nowMillis = now,
        )
        assertTrue(SmsBodyBuilder.isSingleSms(s), s)
        assertTrue(s.contains("CANNOT-WALK"), s)
    }

    @Test fun odiaLandmarkIsStrippedSoSmsStaysAscii() {
        val s = SmsBodyBuilder.sos(
            SosKind.TRAPPED_BY_WATER, 1, 0, SosLocation.Fresh(fix),
            landmark = "ଶିବ ମନ୍ଦିର ପାଖ", details = SosDetails(), nowMillis = now,
        )
        assertTrue(SmsBodyBuilder.isSingleSms(s), s)
    }

    @Test fun staleFixStatesAge() {
        val old = fix.copy(timeMillis = now - 2 * 3600_000L)
        val loc = LocationChooser.choose(fresh = null, lastKnown = old, landmark = "x", nowMillis = now)
        assertTrue(loc is SosLocation.Stale)
        val s = SmsBodyBuilder.sos(SosKind.TRAPPED_BY_WATER, 1, 0, loc, "Ward 5", SosDetails(), now)
        assertTrue(s.contains("@12:32 2h old"), s)
    }

    @Test fun noGpsFallsBackToLandmark() {
        val loc = LocationChooser.choose(null, null, "Ward 5 Astaranga", now)
        assertEquals(SosLocation.LandmarkOnly("Ward 5 Astaranga"), loc)
        val s = SmsBodyBuilder.sos(SosKind.TRAPPED_BY_WATER, 1, 0, loc, "Ward 5 Astaranga", SosDetails(), now)
        assertTrue(s.contains("NO-GPS 14:32 | Ward 5 Astaranga"), s)
        assertFalse(s.contains("maps.google"))
    }

    @Test fun checkInMessages() {
        assertEquals("RM1 SAFE Rina 14:32", SmsBodyBuilder.safe("Rina", now))
        assertEquals("RM1 AT-SHELTER Rina at Astaranga MCS 14:32", SmsBodyBuilder.atShelter("Rina", "Astaranga MCS", now))
        assertEquals("RM1 AT-SHELTER - at shelter 14:32", SmsBodyBuilder.atShelter("", null, now))
    }

    @Test fun pipeInLandmarkIsReplaced() {
        assertEquals("a / b", SmsBodyBuilder.ascii("a | b"))
    }
}

class ShelterTest {
    private fun rec(id: String, lat: Double, lon: Double, verified: Boolean = true) = ShelterRecord(
        id, "S$id", "", "v", "b", "Puri", lat, lon, null, "cyclone",
        source = if (verified) "OSDMA" else "", verifiedOn = if (verified) "2026-10-01" else "",
    )

    @Test fun nearestThreeVerifiedOnly() {
        val list = listOf(
            rec("far", 20.5, 85.8),
            rec("a", 20.01, 85.0), rec("b", 20.02, 85.0), rec("c", 20.03, 85.0), rec("d", 20.04, 85.0),
            rec("unverified", 20.001, 85.0, verified = false),
        )
        val r = NearestShelters.find(20.0, 85.0, list)
        assertEquals(listOf("a", "b", "c"), r.map { it.shelter.id })
        assertEquals(Compass.N, r.first().direction)
    }

    @Test fun nothingWithin10kmGivesEmpty() {
        assertTrue(NearestShelters.find(20.0, 85.0, listOf(rec("far", 20.5, 85.8))).isEmpty())
        assertTrue(NearestShelters.find(20.0, 85.0, emptyList()).isEmpty())
    }

    @Test fun csvParsesAndRejectsBadRows() {
        val csv = """
            # demo
            id,name,name_odia,village,block,district,lat,lon,capacity,type,source,verified_on
            1,"Astaranga MCS, Ward 5",ଅସ୍ତରଙ୍ଗ,Astaranga,Astaranga,Puri,19.98,86.27,1500,cyclone,OSDMA,2026-10-01
            2,No source,,v,b,Puri,19.9,86.2,,cyclone,,
            3,Bad coords,,v,b,Puri,abc,86.2,,cyclone,OSDMA,2026-10-01
        """.trimIndent()
        val r = ShelterCsv.parse(csv)
        assertEquals(1, r.shelters.size)
        assertEquals("Astaranga MCS, Ward 5", r.shelters[0].name)
        assertEquals(1500, r.shelters[0].capacity)
        assertEquals(listOf(4, 5), r.rejectedLines)
    }

    @Test fun typeFilterAndCommunityFlag() {
        val h = rec("h", 20.01, 85.0).copy(type = "hospital", source = "OpenStreetMap")
        val list = listOf(rec("s", 20.02, 85.0), h)
        assertEquals(listOf("h"), NearestShelters.find(20.0, 85.0, list, types = setOf(PlaceType.HOSPITAL)).map { it.shelter.id })
        assertEquals(listOf("s"), NearestShelters.find(20.0, 85.0, list, types = setOf(PlaceType.CYCLONE_SHELTER)).map { it.shelter.id })
        assertTrue(h.isCommunityData)
        assertEquals(PlaceType.CYCLONE_SHELTER, PlaceType.parse("cyclone"))
    }

    @Test fun headerOnlyCsvIsEmpty() {
        val r = ShelterCsv.parse(ShelterCsv.HEADER.joinToString(","))
        assertTrue(r.shelters.isEmpty())
    }
}

class OutboxStateTest {
    @Test fun neverJumpsToDeliveredWithoutSent() {
        assertFalse(OutboxState.SAVED.canMoveTo(OutboxState.DELIVERED))
        assertFalse(OutboxState.HANDED_TO_SMS_APP.canMoveTo(OutboxState.DELIVERED))
        assertTrue(OutboxState.SENT.canMoveTo(OutboxState.DELIVERED))
        assertFalse(OutboxState.DELIVERED.canMoveTo(OutboxState.SAVED))
        assertTrue(OutboxState.SAVED.notSentYet)
        assertFalse(OutboxState.HANDED_TO_SMS_APP.notSentYet)
    }
}

class AlertsTest {
    private val json = """
        {"district":"Ganjam","fetchedAt":"2026-10-27T05:30:00+05:30","alerts":[
          {"id":"imd-1","source":"IMD","level":"red","headline":"Extremely heavy rainfall",
           "issuedAt":"2026-10-27T05:00:00+05:30","validUntil":"2026-10-28T05:00:00+05:30",
           "originalText":"...","simplifiedOdia":null,"extra":1},
          {"id":"cwc-1","source":"SACHET","level":"orange","headline":"River rising",
           "validUntil":"2026-10-26T05:00:00+05:30"}
        ]}
    """.trimIndent()

    @Test fun parsesFeedAndHighestActive() {
        val f = AlertFeedParser.parse(json)
        assertEquals("Ganjam", f.district)
        assertEquals(2, f.alerts.size)
        val now = AlertFeedParser.parseTime("2026-10-27T10:00:00+05:30")!!
        assertEquals(AlertLevel.RED, AlertFeedParser.highestActive(f.alerts, now))
        val later = AlertFeedParser.parseTime("2026-10-29T10:00:00+05:30")!!
        assertEquals(null, AlertFeedParser.highestActive(f.alerts, later))
    }

    @Test fun staleLabel() {
        val t = 1_000_000_000_000L
        assertEquals("Last updated 6 h ago — may be old", Staleness.label(t, t + 6 * 3600_000L))
        assertEquals("Last updated 20 min ago", Staleness.label(t, t + 20 * 60_000L))
    }

    @Test fun refreshPolicy() {
        assertEquals(30, RefreshPolicy.intervalMinutes(AlertLevel.RED))
        assertEquals(30, RefreshPolicy.intervalMinutes(AlertLevel.ORANGE))
        assertEquals(180, RefreshPolicy.intervalMinutes(AlertLevel.YELLOW))
        assertEquals(180, RefreshPolicy.intervalMinutes(null))
    }
}

/** The relay's real output (generated by relay/test fixtures) must parse in the app. */
class RelayContractTest {
    @Test fun parsesRelayOutput() {
        val text = javaClass.getResource("/relay_Ganjam.json")!!.readText()
        val feed = AlertFeedParser.parse(text)
        assertEquals("Ganjam", feed.district)
        val a = feed.alerts.single()
        assertEquals("red", a.level)
        assertEquals("SACHET", a.source)
        assertEquals("IMD Bhubaneswar", a.agency)
        val now = AlertFeedParser.parseTime(feed.fetchedAt)!!
        assertEquals(AlertLevel.RED, AlertFeedParser.highestActive(feed.alerts, now))
    }
}
