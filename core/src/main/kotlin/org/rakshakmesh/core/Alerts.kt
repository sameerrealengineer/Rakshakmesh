package org.rakshakmesh.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.OffsetDateTime

/** Per-district file written by the alert relay (PRD 8.2). */
@Serializable
data class DistrictFeed(
    val district: String,
    val fetchedAt: String,
    val alerts: List<FeedAlert> = emptyList(),
    /** "ok", or "source_unavailable" when the relay could not read the official feed. */
    val status: String? = null,
)

@Serializable
data class FeedAlert(
    val id: String,
    val source: String,
    val level: String,
    val headline: String,
    val issuedAt: String? = null,
    val validUntil: String? = null,
    val originalText: String = "",
    val simplifiedOdia: String? = null,
    @SerialName("agency") val agency: String? = null,
)

enum class AlertLevel(val rank: Int) {
    GREEN(0), YELLOW(1), ORANGE(2), RED(3);

    companion object {
        fun parse(s: String): AlertLevel = entries.firstOrNull { it.name.equals(s.trim(), true) } ?: YELLOW
    }
}

object AlertFeedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): DistrictFeed = json.decodeFromString(DistrictFeed.serializer(), text)

    /** Parses ISO-8601 with offset; returns epoch millis or null. */
    fun parseTime(s: String?): Long? =
        s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    /** Highest level among alerts still valid at [nowMillis]. */
    fun highestActive(alerts: List<FeedAlert>, nowMillis: Long): AlertLevel? =
        highestActiveOf(alerts.map { it.level to parseTime(it.validUntil) }, nowMillis)

    /** Same rule for cached rows: pairs of (level, validUntil millis or null = no expiry given). */
    fun highestActiveOf(levelsAndExpiry: List<Pair<String, Long?>>, nowMillis: Long): AlertLevel? =
        levelsAndExpiry.filter { (_, until) -> until == null || until > nowMillis }
            .maxOfOrNull { AlertLevel.parse(it.first) }
}

/** Age label for cached data. Stale data is never hidden (FR-ALR-04). */
object Staleness {
    const val STALE_AFTER_MIN = 60L * 3

    data class Age(val minutes: Long) {
        val isStale: Boolean get() = minutes >= STALE_AFTER_MIN

        /** English short form, e.g. "6 h". Localised strings are built in the app. */
        val short: String
            get() = when {
                minutes < 1 -> "<1 min"
                minutes < 60 -> "$minutes min"
                minutes < 60 * 48 -> "${minutes / 60} h"
                else -> "${minutes / (60 * 24)} d"
            }
    }

    fun age(fetchedAtMillis: Long, nowMillis: Long): Age =
        Age(Duration.ofMillis((nowMillis - fetchedAtMillis).coerceAtLeast(0)).toMinutes())

    fun label(fetchedAtMillis: Long, nowMillis: Long): String {
        val a = age(fetchedAtMillis, nowMillis)
        return if (a.isStale) "Last updated ${a.short} ago — may be old" else "Last updated ${a.short} ago"
    }
}

/** Refresh interval rule (FR-ALR-02): 3 h normally, 30 min while orange/red is active. */
object RefreshPolicy {
    fun intervalMinutes(highest: AlertLevel?): Long =
        if (highest != null && highest.rank >= AlertLevel.ORANGE.rank) 30 else 180
}
