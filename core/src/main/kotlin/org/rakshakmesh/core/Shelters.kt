package org.rakshakmesh.core

/** One shelter record. Only records with a [source] and [verifiedOn] are ever shown (PRD 5.4). */
data class ShelterRecord(
    val id: String,
    val name: String,
    val nameOdia: String,
    val village: String,
    val block: String,
    val district: String,
    val lat: Double,
    val lon: Double,
    val capacity: Int?,
    val type: String,
    val source: String,
    val verifiedOn: String,
) {
    val isVerified: Boolean get() = source.isNotBlank() && verifiedOn.isNotBlank()
}

data class NearbyShelter(
    val shelter: ShelterRecord,
    val distanceKm: Double,
    val direction: Compass,
)

object NearestShelters {
    const val DEFAULT_MAX_KM = 10.0

    /**
     * Up to [limit] verified shelters within [maxKm], nearest first.
     * An empty result means the UI must say "No verified shelter in our data — call 1077"
     * and never guess (FR-SHL-04).
     */
    fun find(
        lat: Double,
        lon: Double,
        shelters: List<ShelterRecord>,
        limit: Int = 3,
        maxKm: Double = DEFAULT_MAX_KM,
    ): List<NearbyShelter> = shelters.asSequence()
        .filter { it.isVerified }
        .map {
            NearbyShelter(
                shelter = it,
                distanceKm = Geo.distanceKm(lat, lon, it.lat, it.lon),
                direction = Geo.compass(Geo.bearingDeg(lat, lon, it.lat, it.lon)),
            )
        }
        .filter { it.distanceKm <= maxKm }
        .sortedBy { it.distanceKm }
        .take(limit)
        .toList()
}

/**
 * Parses the shelter CSV that ships in the APK.
 * Header: id,name,name_odia,village,block,district,lat,lon,capacity,type,source,verified_on
 * Rows with missing id/name/coords, or without source/verified_on, are rejected.
 */
object ShelterCsv {
    val HEADER = listOf(
        "id", "name", "name_odia", "village", "block", "district",
        "lat", "lon", "capacity", "type", "source", "verified_on",
    )

    data class Result(val shelters: List<ShelterRecord>, val rejectedLines: List<Int>)

    fun parse(text: String): Result {
        val lines = text.lines()
        val headerIdx = lines.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (headerIdx < 0) return Result(emptyList(), emptyList())
        val header = splitLine(lines[headerIdx]).map { it.trim().lowercase() }
        require(header == HEADER) { "Unexpected shelter CSV header: $header" }

        val ok = mutableListOf<ShelterRecord>()
        val rejected = mutableListOf<Int>()
        for (i in headerIdx + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val record = parseRow(splitLine(line))
            if (record == null) rejected += i + 1 else ok += record
        }
        return Result(ok, rejected)
    }

    private fun parseRow(c: List<String>): ShelterRecord? {
        if (c.size != HEADER.size) return null
        val f = c.map { it.trim() }
        val lat = f[6].toDoubleOrNull() ?: return null
        val lon = f[7].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (f[0].isEmpty() || f[1].isEmpty()) return null
        val r = ShelterRecord(
            id = f[0], name = f[1], nameOdia = f[2], village = f[3], block = f[4],
            district = f[5], lat = lat, lon = lon, capacity = f[8].toIntOrNull(),
            type = f[9].ifEmpty { "cyclone" }, source = f[10], verifiedOn = f[11],
        )
        return if (r.isVerified) r else null
    }

    /** Minimal CSV split supporting double-quoted fields with "" escapes. */
    internal fun splitLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
