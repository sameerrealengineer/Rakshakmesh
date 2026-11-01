package org.rakshakmesh.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.rakshakmesh.core.AlertFeedParser
import org.rakshakmesh.core.MessageType
import org.rakshakmesh.core.OutboxState
import org.rakshakmesh.core.ShelterCsv
import org.rakshakmesh.core.SosLocation
import org.rakshakmesh.device.Phone
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Saves first, then hands to the SMS app. States are never merged (PRD principle 2). */
class OutboxRepo(private val context: Context, private val db: AppDatabase) {

    enum class HandOff { HANDED, NO_RECIPIENTS, NO_SIGNAL, NO_SMS_APP }

    suspend fun recipients(): List<String> = db.contacts().all().map { it.phone }.filter { it.isNotBlank() }

    /** Step 1: always save locally (state SAVED). */
    suspend fun save(type: MessageType, body: String, recipients: List<String>, location: SosLocation?): OutboxMessage {
        val now = System.currentTimeMillis()
        val fix = location?.fixOrNull
        val msg = OutboxMessage(
            id = UUID.randomUUID().toString(),
            type = type.name,
            body = body,
            recipients = recipients.joinToString(";"),
            lat = fix?.lat,
            lon = fix?.lon,
            accuracyM = fix?.accuracyM,
            fixAgeSec = (location as? SosLocation.Stale)?.ageSec ?: if (fix != null) 0 else null,
            createdAt = now,
            state = OutboxState.SAVED.name,
        )
        db.outbox().insert(msg)
        return msg
    }

    /** Step 2: try to hand the saved message to the SMS app. */
    suspend fun handToSmsApp(id: String): HandOff {
        val msg = db.outbox().get(id) ?: return HandOff.NO_SMS_APP
        val to = msg.recipients.split(';').filter { it.isNotBlank() }
        val result = when {
            to.isEmpty() -> HandOff.NO_RECIPIENTS
            // In airplane mode the SMS cannot leave; keep it in the outbox.
            Phone.isAirplaneModeOn(context) -> HandOff.NO_SIGNAL
            !Phone.composeSms(context, to, msg.body) -> HandOff.NO_SMS_APP
            else -> HandOff.HANDED
        }
        val current = OutboxState.valueOf(msg.state)
        val next = when (result) {
            HandOff.HANDED -> OutboxState.HANDED_TO_SMS_APP
            HandOff.NO_SMS_APP -> OutboxState.FAILED
            HandOff.NO_RECIPIENTS, HandOff.NO_SIGNAL -> current
        }
        if (next != current && !current.canMoveTo(next)) return result
        db.outbox().update(
            msg.copy(
                state = next.name,
                attempts = msg.attempts + if (result == HandOff.HANDED) 1 else 0,
                lastError = when (result) {
                    HandOff.HANDED -> null
                    HandOff.NO_RECIPIENTS -> "no_recipients"
                    HandOff.NO_SIGNAL -> "airplane_mode"
                    HandOff.NO_SMS_APP -> "no_sms_app"
                },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return result
    }

    suspend fun unsent(): List<OutboxMessage> = db.outbox().unsent()
}

/** Fetches the relay's per-district JSON and caches it. Called by AlertWorker only when online. */
class AlertRepo(private val db: AppDatabase, private val settings: SettingsStore) {

    suspend fun refresh(district: String): Result<Int> = withContext(Dispatchers.IO) {
        val s = settings.get()
        val now = System.currentTimeMillis()
        runCatching {
            val url = "${s.relayUrl}/alerts/${URLEncoder.encode(district, "UTF-8").replace("+", "%20")}.json"
            val text = httpGet(url)
            val feed = AlertFeedParser.parse(text)
            val fetchedAt = AlertFeedParser.parseTime(feed.fetchedAt) ?: now
            val rows = feed.alerts.map {
                AlertCache(
                    alertId = it.id, source = it.source, agency = it.agency, district = district,
                    level = it.level.lowercase(), headline = it.headline, originalText = it.originalText,
                    simplifiedOdia = it.simplifiedOdia, issuedAt = AlertFeedParser.parseTime(it.issuedAt),
                    validUntil = AlertFeedParser.parseTime(it.validUntil), fetchedAt = fetchedAt,
                )
            }
            db.alerts().replaceDistrict(district, rows)
            db.alerts().prune(now - 7L * 24 * 3600_000)
            // The age shown to the user is the relay's fetch time, not ours: that is when
            // the official source was last read.
            settings.recordFetch(true, fetchedAt, null)
            rows.size
        }.onFailure { settings.recordFetch(false, now, it.message ?: it.javaClass.simpleName) }
    }

    companion object {
        fun httpGet(url: String, timeoutMs: Int = 20_000): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            try {
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}

@Serializable
private data class ChecklistJson(
    val id: String,
    val phase: String,
    val or: String,
    val hi: String,
    val en: String,
    val warning: Boolean = false,
)

/** Loads bundled shelters and checklist on first run, and again when the APK's content changes. */
class Seeder(private val context: Context, private val db: AppDatabase) {
    suspend fun seed() = withContext(Dispatchers.IO) {
        seedShelters()
        seedChecklist()
    }

    private suspend fun seedShelters() {
        val text = context.assets.open("shelters.csv").bufferedReader().use { it.readText() }
        val parsed = ShelterCsv.parse(text)
        db.shelters().replaceAll(parsed.shelters.map(Shelter::from))
    }

    private suspend fun seedChecklist() {
        val text = context.assets.open("checklist.json").bufferedReader().use { it.readText() }
        val items = Json { ignoreUnknownKeys = true }.decodeFromString<List<ChecklistJson>>(text)
        val done = db.checklist().all().associate { it.id to it.done }
        db.checklist().upsertAll(
            items.mapIndexed { i, it ->
                val audio = listOf("mp3", "wav").map { ext -> "audio/${it.id}_or.$ext" }.firstOrNull(::assetExists)
                ChecklistItem(
                    id = it.id, phase = it.phase, textOr = it.or, textHi = it.hi, textEn = it.en,
                    audioFile = audio, done = done[it.id] ?: false, sortOrder = i, isWarning = it.warning,
                )
            },
        )
    }

    private fun assetExists(path: String): Boolean =
        runCatching { context.assets.open(path).close(); true }.getOrDefault(false)
}
