package org.rakshakmesh.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.rakshakmesh.core.ShelterRecord

// Tables follow PRD section 8.1. Nothing here is ever uploaded.

@Entity
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val village: String = "",
    val block: String = "",
    val district: String = "",
    val landmark: String = "",
    val bloodGroup: String? = null,
    val cannotWalk: Boolean = false,
    val pregnant: Boolean = false,
    val infant: Boolean = false,
    val language: String = "or",
)

@Entity
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val phone: String,
    /** "family" (max 5) or "volunteer" (max 1). */
    val role: String,
)

@Entity
data class OutboxMessage(
    @PrimaryKey val id: String,
    /** org.rakshakmesh.core.MessageType name */
    val type: String,
    val body: String,
    /** Phone numbers joined with ';' */
    val recipients: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val fixAgeSec: Long? = null,
    val createdAt: Long,
    /** org.rakshakmesh.core.OutboxState name */
    val state: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long = createdAt,
)

@Entity
data class AlertCache(
    @PrimaryKey val alertId: String,
    val source: String,
    val agency: String?,
    val district: String,
    val level: String,
    val headline: String,
    val originalText: String,
    val simplifiedOdia: String?,
    val issuedAt: Long?,
    val validUntil: Long?,
    val fetchedAt: Long,
    /** Received from a nearby phone over the mesh, not downloaded by this phone. */
    val viaMesh: Boolean = false,
)

@Entity
data class Shelter(
    @PrimaryKey val id: String,
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
    fun toRecord() = ShelterRecord(id, name, nameOdia, village, block, district, lat, lon, capacity, type, source, verifiedOn)

    companion object {
        fun from(r: ShelterRecord) = Shelter(
            r.id, r.name, r.nameOdia, r.village, r.block, r.district, r.lat, r.lon, r.capacity, r.type, r.source, r.verifiedOn,
        )
    }
}

@Entity
data class ChecklistItem(
    @PrimaryKey val id: String,
    /** before / during / after */
    val phase: String,
    val textOr: String,
    val textHi: String,
    val textEn: String,
    val audioFile: String?,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    /** Hard warnings are shown at the top and cannot be ticked (FR-CHK-03). */
    val isWarning: Boolean = false,
)

/** A message sent or received over the offline phone-to-phone mesh. */
@Entity
data class MeshMessage(
    @PrimaryKey val id: String,
    /** org.rakshakmesh.core.mesh.PacketType name */
    val type: String,
    val originId: String,
    val originName: String,
    val createdAt: Long,
    val expiresAt: Long,
    val hops: Int,
    val maxHops: Int,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,
    val body: String,
    val fileName: String?,
    val mime: String?,
    val size: Long?,
    val sha256: String?,
    /** Absolute path of the media file on this phone, if any. */
    val localPath: String?,
    val outgoing: Boolean,
    val receivedAt: Long,
    /** How many nearby phones this phone handed it to. */
    val sharedWith: Int = 0,
)

@Dao
interface MeshDao {
    @Query("SELECT * FROM MeshMessage WHERE type != 'HELLO' ORDER BY receivedAt DESC LIMIT 500")
    fun observeFeed(): Flow<List<MeshMessage>>

    @Query("SELECT * FROM MeshMessage WHERE type = 'SOS' AND expiresAt > :now ORDER BY receivedAt DESC")
    fun observeActiveSos(now: Long): Flow<List<MeshMessage>>

    @Query("SELECT * FROM MeshMessage WHERE id = :id")
    suspend fun get(id: String): MeshMessage?

    @Query("SELECT id FROM MeshMessage")
    suspend fun allIds(): List<String>

    /** Packets worth handing to a phone that just connected (newest first). */
    @Query("SELECT * FROM MeshMessage WHERE expiresAt > :now AND type IN ('SOS','SAFE','TEXT','ALERT','VOICE') ORDER BY createdAt DESC LIMIT 100")
    suspend fun syncable(now: Long): List<MeshMessage>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MeshMessage): Long

    @Query("UPDATE MeshMessage SET sharedWith = sharedWith + :n WHERE id = :id")
    suspend fun addShared(id: String, n: Int)

    @Query("DELETE FROM MeshMessage WHERE expiresAt < :before")
    suspend fun prune(before: Long)

    @Query("DELETE FROM MeshMessage")
    suspend fun clearAll()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile WHERE id = 1")
    fun observe(): Flow<Profile?>

    @Query("SELECT * FROM Profile WHERE id = 1")
    suspend fun get(): Profile?

    @Upsert
    suspend fun save(p: Profile)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM Contact ORDER BY role, id")
    fun observeAll(): Flow<List<Contact>>

    @Query("SELECT * FROM Contact ORDER BY role, id")
    suspend fun all(): List<Contact>

    @Query("DELETE FROM Contact")
    suspend fun clear()

    @Insert
    suspend fun insertAll(c: List<Contact>)

    @Transaction
    suspend fun replaceAll(c: List<Contact>) {
        clear()
        insertAll(c)
    }
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM OutboxMessage ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OutboxMessage>>

    @Query("SELECT * FROM OutboxMessage WHERE id = :id")
    suspend fun get(id: String): OutboxMessage?

    @Query("SELECT * FROM OutboxMessage WHERE state IN ('SAVED','FAILED') ORDER BY createdAt")
    suspend fun unsent(): List<OutboxMessage>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(m: OutboxMessage)

    @Upsert
    suspend fun update(m: OutboxMessage)

    /** Only the user clears the outbox; nothing is deleted automatically. */
    @Query("DELETE FROM OutboxMessage")
    suspend fun clearAll()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM AlertCache WHERE district = :district ORDER BY issuedAt DESC")
    fun observe(district: String): Flow<List<AlertCache>>

    @Query("SELECT * FROM AlertCache WHERE district = :district")
    suspend fun forDistrict(district: String): List<AlertCache>

    @Query("DELETE FROM AlertCache WHERE district = :district AND alertId NOT IN (:keep)")
    suspend fun deleteOthers(district: String, keep: List<String>)

    @Query("DELETE FROM AlertCache WHERE district = :district")
    suspend fun deleteDistrict(district: String)

    /** Old alerts kept 7 days (PRD 8.1). */
    @Query("DELETE FROM AlertCache WHERE fetchedAt < :before")
    suspend fun prune(before: Long)

    @Upsert
    suspend fun upsertAll(a: List<AlertCache>)

    @Transaction
    suspend fun replaceDistrict(district: String, alerts: List<AlertCache>) {
        if (alerts.isEmpty()) deleteDistrict(district) else deleteOthers(district, alerts.map { it.alertId })
        upsertAll(alerts)
    }
}

@Dao
interface ShelterDao {
    @Query("SELECT * FROM Shelter")
    fun observeAll(): Flow<List<Shelter>>

    @Query("SELECT * FROM Shelter")
    suspend fun all(): List<Shelter>

    @Query("SELECT COUNT(*) FROM Shelter")
    suspend fun count(): Int

    @Query("DELETE FROM Shelter")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(s: List<Shelter>)

    @Transaction
    suspend fun replaceAll(s: List<Shelter>) {
        clear()
        upsertAll(s)
    }
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM ChecklistItem ORDER BY isWarning DESC, sortOrder")
    fun observeAll(): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM ChecklistItem")
    suspend fun all(): List<ChecklistItem>

    @Upsert
    suspend fun upsertAll(items: List<ChecklistItem>)

    @Query("UPDATE ChecklistItem SET done = :done WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean)

    @Query("UPDATE ChecklistItem SET done = 0")
    suspend fun resetAll()
}

@Database(
    entities = [Profile::class, Contact::class, OutboxMessage::class, AlertCache::class, Shelter::class, ChecklistItem::class, MeshMessage::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun contacts(): ContactDao
    abstract fun outbox(): OutboxDao
    abstract fun alerts(): AlertDao
    abstract fun shelters(): ShelterDao
    abstract fun checklist(): ChecklistDao
    abstract fun mesh(): MeshDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rakshakmesh.db")
                // Only test builds existed before v2; nothing to migrate.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
