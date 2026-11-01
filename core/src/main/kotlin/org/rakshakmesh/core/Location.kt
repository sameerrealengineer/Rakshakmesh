package org.rakshakmesh.core

/** A raw GPS fix. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float?,
    val timeMillis: Long,
)

/** What location goes into an SOS (FR-SOS-03). Never blank. */
sealed interface SosLocation {
    /** Fresh fix from this SOS. */
    data class Fresh(val fix: Fix) : SosLocation

    /** Older fix; the UI and SMS must say how old. */
    data class Stale(val fix: Fix, val ageSec: Long) : SosLocation

    /** No fix at all; only the landmark/village saved at onboarding. */
    data class LandmarkOnly(val landmark: String) : SosLocation

    val fixOrNull: Fix?
        get() = when (this) {
            is Fresh -> fix
            is Stale -> fix
            is LandmarkOnly -> null
        }
}

object LocationChooser {
    /** A fix younger than this counts as fresh. */
    const val FRESH_MAX_AGE_SEC = 120L

    fun choose(fresh: Fix?, lastKnown: Fix?, landmark: String, nowMillis: Long): SosLocation {
        val best = fresh ?: lastKnown
        if (best != null) {
            val age = ((nowMillis - best.timeMillis) / 1000).coerceAtLeast(0)
            return if (age <= FRESH_MAX_AGE_SEC) SosLocation.Fresh(best) else SosLocation.Stale(best, age)
        }
        return SosLocation.LandmarkOnly(landmark)
    }
}
