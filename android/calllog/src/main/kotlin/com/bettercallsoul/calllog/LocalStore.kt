package com.bettercallsoul.calllog

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A private label for a number that isn't saved in the address book. Never leaves the phone. */
@Entity(tableName = "tags")
data class NumberTag(
    @PrimaryKey val number: String,
    val label: String,
    val updatedAt: Long,
)

/** Last server answer for a number, so it can be shown without a network round trip. */
@Entity(tableName = "spam_cache")
data class SpamCacheEntry(
    @PrimaryKey val number: String,
    val verdict: String,
    val spamVotes: Int,
    val category: String?,
    val callerName: String?,
    val fetchedAt: Long,
)

/** Numbers the user has already answered "was this spam?" for. */
@Entity(tableName = "answered")
data class AnsweredNumber(
    @PrimaryKey val number: String,
    val answeredAt: Long,
)

/**
 * A contact deleted from this app, kept on the phone so it can be put back. [payload] holds the
 * contact's fields as JSON; the photo, if any, is a file named after [id].
 */
@Entity(tableName = "trash")
data class TrashedContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accountName: String?,
    val accountType: String?,
    val payload: String,
    val hasPhoto: Boolean,
    val deletedAt: Long,
) {
    val account: ContactAccount get() = ContactAccount(accountName, accountType)
}

/** A call copied from the phone's log for the statistics; see [CallHistory]. [type] is the log's own. */
@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey val id: Long,
    val number: String,
    val type: Int,
    val date: Long,
    val seconds: Long,
)

/** A WhatsApp call read from its notifications; see [WhatsAppCallLog]. [outcome] is a CallOutcome's name. */
@Entity(tableName = "whatsapp_calls")
data class WhatsAppCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String,
    val video: Boolean,
    val outcome: String,
    val date: Long,
    val seconds: Long,
)

/** How long an incoming call rang before it was missed or declined; the phone's log doesn't keep this. */
@Entity(tableName = "ring_times")
data class RingTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val date: Long,
    val seconds: Long,
)

@Dao
interface LocalDao {
    @Query("SELECT * FROM tags")
    fun observeTags(): Flow<List<NumberTag>>

    @Query("SELECT * FROM tags WHERE number = :number")
    suspend fun tag(number: String): NumberTag?

    @Upsert
    suspend fun saveTag(tag: NumberTag)

    @Query("DELETE FROM tags WHERE number = :number")
    suspend fun deleteTag(number: String)

    @Query("SELECT * FROM spam_cache WHERE number = :number")
    suspend fun cached(number: String): SpamCacheEntry?

    @Upsert
    suspend fun saveCached(entry: SpamCacheEntry)

    @Query("SELECT EXISTS(SELECT 1 FROM answered WHERE number = :number)")
    suspend fun isAnswered(number: String): Boolean

    @Upsert
    suspend fun markAnswered(entry: AnsweredNumber)

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TrashedContact>>

    @Query("SELECT * FROM trash WHERE deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<TrashedContact>

    @Upsert
    suspend fun saveTrashed(entry: TrashedContact): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun deleteTrashed(id: Long)

    @Query("SELECT * FROM call_records ORDER BY date DESC")
    fun observeCallRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records")
    suspend fun callRecords(): List<CallRecord>

    @Upsert
    suspend fun saveCallRecords(records: List<CallRecord>)

    @Query("DELETE FROM call_records WHERE date < :before")
    suspend fun deleteCallRecordsBefore(before: Long)

    @Query("DELETE FROM call_records WHERE id IN (:ids)")
    suspend fun deleteCallRecords(ids: List<Long>)

    @Query("SELECT * FROM whatsapp_calls ORDER BY date DESC")
    fun observeWhatsAppCalls(): Flow<List<WhatsAppCall>>

    @Query("SELECT * FROM whatsapp_calls ORDER BY date DESC")
    suspend fun whatsAppCalls(): List<WhatsAppCall>

    @Upsert
    suspend fun saveWhatsAppCall(call: WhatsAppCall)

    @Query("DELETE FROM whatsapp_calls WHERE date < :before")
    suspend fun deleteWhatsAppCallsBefore(before: Long)

    @Query("DELETE FROM whatsapp_calls WHERE id IN (:ids)")
    suspend fun deleteWhatsAppCalls(ids: List<Long>)

    @Query("SELECT * FROM ring_times")
    fun observeRingTimes(): Flow<List<RingTime>>

    @Query("SELECT * FROM ring_times")
    suspend fun ringTimes(): List<RingTime>

    @Upsert
    suspend fun saveRingTime(time: RingTime)

    @Query("DELETE FROM ring_times WHERE date < :before")
    suspend fun deleteRingTimesBefore(before: Long)
}

@Database(
    entities = [NumberTag::class, SpamCacheEntry::class, AnsweredNumber::class, TrashedContact::class, CallRecord::class, WhatsAppCall::class, RingTime::class],
    version = 5,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        fun create(context: Context): LocalDatabase =
            Room.databaseBuilder(context, LocalDatabase::class.java, "better-call-soul.db")
                .addMigrations(ADD_TRASH, ADD_CALL_RECORDS, ADD_WHATSAPP_CALLS, ADD_RING_TIMES)
                .build()

        private val ADD_RING_TIMES = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ring_times` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`number` TEXT NOT NULL, `date` INTEGER NOT NULL, `seconds` INTEGER NOT NULL)",
                )
            }
        }

        private val ADD_WHATSAPP_CALLS = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `whatsapp_calls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `number` TEXT NOT NULL, `video` INTEGER NOT NULL, `outcome` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `seconds` INTEGER NOT NULL)",
                )
            }
        }

        private val ADD_CALL_RECORDS = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `call_records` (`id` INTEGER NOT NULL, `number` TEXT NOT NULL, " +
                        "`type` INTEGER NOT NULL, `date` INTEGER NOT NULL, `seconds` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        private val ADD_TRASH = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trash` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `accountName` TEXT, `accountType` TEXT, `payload` TEXT NOT NULL, " +
                        "`hasPhoto` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL)",
                )
            }
        }
    }
}
