package org.rakshakmesh.core

/** The 30 districts of Odisha. Names match the relay's file names. */
object Districts {
    val ALL: List<String> = listOf(
        "Angul", "Balangir", "Balasore", "Bargarh", "Bhadrak", "Boudh", "Cuttack",
        "Deogarh", "Dhenkanal", "Gajapati", "Ganjam", "Jagatsinghpur", "Jajpur",
        "Jharsuguda", "Kalahandi", "Kandhamal", "Kendrapara", "Kendujhar", "Khordha",
        "Koraput", "Malkangiri", "Mayurbhanj", "Nabarangpur", "Nayagarh", "Nuapada",
        "Puri", "Rayagada", "Sambalpur", "Subarnapur", "Sundargarh",
    )

    /** Approximate district HQ coordinates, used only to suggest a district from GPS. */
    val HQ: Map<String, Pair<Double, Double>> = mapOf(
        "Angul" to (20.84 to 85.10), "Balangir" to (20.71 to 83.48), "Balasore" to (21.49 to 86.93),
        "Bargarh" to (21.33 to 83.62), "Bhadrak" to (21.06 to 86.50), "Boudh" to (20.84 to 84.32),
        "Cuttack" to (20.46 to 85.88), "Deogarh" to (21.54 to 84.73), "Dhenkanal" to (20.66 to 85.60),
        "Gajapati" to (18.85 to 84.13), "Ganjam" to (19.39 to 84.88), "Jagatsinghpur" to (20.26 to 86.17),
        "Jajpur" to (20.85 to 86.33), "Jharsuguda" to (21.86 to 84.01), "Kalahandi" to (19.91 to 83.17),
        "Kandhamal" to (20.47 to 84.23), "Kendrapara" to (20.50 to 86.42), "Kendujhar" to (21.63 to 85.58),
        "Khordha" to (20.18 to 85.62), "Koraput" to (18.81 to 82.71), "Malkangiri" to (18.35 to 81.89),
        "Mayurbhanj" to (21.94 to 86.73), "Nabarangpur" to (19.23 to 82.55), "Nayagarh" to (20.13 to 85.10),
        "Nuapada" to (20.82 to 82.53), "Puri" to (19.81 to 85.83), "Rayagada" to (19.17 to 83.42),
        "Sambalpur" to (21.47 to 83.97), "Subarnapur" to (20.84 to 83.90), "Sundargarh" to (22.12 to 84.03),
    )

    /** Nearest district HQ — a suggestion only; the user confirms (FR-ALR-01). */
    fun suggest(lat: Double, lon: Double): String =
        HQ.minBy { (_, p) -> Geo.distanceKm(lat, lon, p.first, p.second) }.key
}
