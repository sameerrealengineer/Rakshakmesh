package org.rakshakmesh.core

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Straight-line distance and direction helpers. No road routing is promised (FR-SHL-02). */
object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Great-circle distance in kilometres. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 to point 2, in degrees 0..360 (0 = north). */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 8-point compass direction for a bearing. */
    fun compass(bearingDeg: Double): Compass {
        val idx = ((bearingDeg % 360 + 360) % 360 / 45.0).roundToInt() % 8
        return Compass.entries[idx]
    }

    /** 4 decimals is about 11 m (PRD 5.5). Always uses '.' as decimal separator. */
    fun formatCoord(value: Double): String = String.format(Locale.US, "%.4f", value)
}

enum class Compass { N, NE, E, SE, S, SW, W, NW }
