package org.rakshakmesh.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SosKind(val code: String) {
    TRAPPED_BY_WATER("TRAPPED-WATER"),
    INJURED_MEDICAL("INJURED-MEDICAL"),
    NEED_EVACUATION("NEED-EVACUATION"),
}

/** Optional details ticked at onboarding (FR-SOS-08). Nothing else medical is stored. */
data class SosDetails(
    val bloodGroup: String? = null,
    val cannotWalk: Boolean = false,
    val pregnant: Boolean = false,
    val infant: Boolean = false,
)

/**
 * Builds the SMS texts. Output is plain ASCII and at most 160 characters, so it fits one
 * GSM-7 SMS (Odia script would force UCS-2: 70 chars per SMS). FR-SOS-04, AT-05.
 */
object SmsBodyBuilder {
    const val MAX_LEN = 160
    const val PROTOCOL = "RM1"
    private const val SEP = " | "

    fun sos(
        kind: SosKind,
        people: Int,
        elderly: Int,
        location: SosLocation,
        landmark: String,
        details: SosDetails,
        nowMillis: Long,
        zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    ): String {
        val head = buildString {
            append("SOS ").append(PROTOCOL).append(' ').append(kind.code)
            append(' ').append(people.coerceIn(1, 99)).append("ppl")
            if (elderly > 0) append(' ').append(elderly.coerceAtMost(99)).append("eld")
        }
        val fix = location.fixOrNull
        val loc = when (location) {
            is SosLocation.Fresh -> coords(location.fix) + " " + hhmm(location.fix.timeMillis, zone)
            is SosLocation.Stale ->
                coords(location.fix) + " @" + hhmm(location.fix.timeMillis, zone) + " " + age(location.ageSec) + " old"
            is SosLocation.LandmarkOnly -> "NO-GPS " + hhmm(nowMillis, zone)
        }
        val medical = buildList {
            details.bloodGroup?.let { ascii(it).takeIf(String::isNotBlank)?.let(::add) }
            if (details.cannotWalk) add("CANNOT-WALK")
            if (details.pregnant) add("PREGNANT")
            if (details.infant) add("INFANT")
        }.joinToString(" ")
        val link = fix?.let { "maps.google.com/?q=${Geo.formatCoord(it.lat)},${Geo.formatCoord(it.lon)}" }
        var mark = ascii(landmark)

        fun assemble(withMedical: Boolean, withLink: Boolean) =
            listOfNotNull(
                head, loc, mark.ifBlank { null },
                medical.takeIf { withMedical && it.isNotBlank() },
                link.takeIf { withLink },
            ).joinToString(SEP)

        // Drop the least important parts first: maps link, then medical flags, then trim landmark.
        assemble(withMedical = true, withLink = true).let { if (it.length <= MAX_LEN) return it }
        assemble(withMedical = true, withLink = false).let { if (it.length <= MAX_LEN) return it }
        val noMedical = assemble(withMedical = false, withLink = false)
        if (noMedical.length <= MAX_LEN) return noMedical
        val overflow = noMedical.length - MAX_LEN
        mark = mark.dropLast(overflow).trimEnd()
        return assemble(withMedical = false, withLink = false).take(MAX_LEN)
    }

    fun safe(name: String, nowMillis: Long, zone: ZoneId = ZoneId.of("Asia/Kolkata")): String =
        fit("$PROTOCOL SAFE ${ascii(name).ifBlank { "-" }} ${hhmm(nowMillis, zone)}")

    fun atShelter(
        name: String,
        shelterName: String?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    ): String {
        val who = ascii(name).ifBlank { "-" }
        val where = shelterName?.let(::ascii)?.takeIf { it.isNotBlank() }?.let { " at $it" } ?: " at shelter"
        return fit("$PROTOCOL AT-SHELTER $who$where ${hhmm(nowMillis, zone)}")
    }

    /** Keeps printable ASCII only; collapses whitespace. Odia text typed by the user is dropped. */
    fun ascii(s: String): String =
        s.filter { it in ' '..'~' }.replace('|', '/').replace(Regex("\\s+"), " ").trim()

    fun isSingleSms(s: String): Boolean = s.length <= MAX_LEN && s.all { it in ' '..'~' }

    private fun fit(s: String) = if (s.length <= MAX_LEN) s else s.take(MAX_LEN)

    private fun coords(f: Fix): String {
        val acc = f.accuracyM?.let { " +-${it.toInt().coerceAtLeast(1)}m" } ?: ""
        return "${Geo.formatCoord(f.lat)},${Geo.formatCoord(f.lon)}$acc"
    }

    private fun hhmm(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(millis))

    private fun age(sec: Long): String = when {
        sec < 3600 -> "${(sec / 60).coerceAtLeast(1)}min"
        sec < 86400 -> "${sec / 3600}h"
        else -> "${sec / 86400}d"
    }
}
