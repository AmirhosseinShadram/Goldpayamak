package ir.goldpayamak.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

object Status {
    const val PENDING = "PENDING"
    const val SENDING = "SENDING"
    const val SENT = "SENT"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
    const val SELF = "SELF" // پیام طرف ۲ (خود کاربر): فقط ثبت می‌شود، ارسال نمی‌شود

    const val REQ_ACTIVE = "ACTIVE"
    const val REQ_SUPERSEDED = "SUPERSEDED"
    const val REQ_CANCELLED = "CANCELLED"
    const val REQ_RECORD = "RECORD" // خود ردیف اصلاحیه/لغو
}

@Entity(tableName = "contacts", indices = [Index("phone", unique = true)])
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
)

@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootId: Long,
    val parentId: Long?,
    val kind: String,
    val price: Long,
    val userName: String,
    val createdAt: Long,
    val status: String,
)

@Entity(tableName = "parties", indices = [Index("requestId")])
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: Long,
    val position: Int,
    val name: String,
    val phone: String,
    val isSelf: Boolean,
)

@Entity(tableName = "messages", indices = [Index("requestId"), Index("phone"), Index("createdAt")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: Long,
    val name: String,
    val phone: String,
    val kind: String,
    val text: String,
    val price: Long,
    val status: String,
    val parts: Int = 0,
    val sentOk: Int = 0,
    val deliveredOk: Int = 0,
    val failed: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface AppDao {
    // مخاطبین
    @Query("SELECT * FROM contacts ORDER BY name COLLATE LOCALIZED")
    fun contacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactIgnore(c: ContactEntity): Long

    @Update
    suspend fun updateContact(c: ContactEntity)

    @Delete
    suspend fun deleteContact(c: ContactEntity)

    @Query("SELECT * FROM contacts WHERE phone = :phone LIMIT 1")
    suspend fun contactByPhone(phone: String): ContactEntity?

    // درخواست‌ها
    @Insert
    suspend fun insertRequest(r: RequestEntity): Long

    @Query("UPDATE requests SET rootId = :rootId WHERE id = :id")
    suspend fun setRoot(id: Long, rootId: Long)

    @Query("UPDATE requests SET status = :status WHERE id = :id")
    suspend fun setRequestStatus(id: Long, status: String)

    @Insert
    suspend fun insertParties(p: List<PartyEntity>)

    @Insert
    suspend fun insertMessages(m: List<MessageEntity>)

    @Query("SELECT * FROM requests WHERE kind != 'CANCEL' ORDER BY createdAt DESC, id DESC")
    fun requests(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id")
    fun request(id: Long): Flow<RequestEntity?>

    @Query("SELECT * FROM requests WHERE id = :id")
    suspend fun requestOnce(id: Long): RequestEntity?

    @Query("SELECT * FROM parties WHERE requestId = :id ORDER BY position")
    suspend fun partiesOnce(id: Long): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE requestId = :id ORDER BY position")
    fun parties(id: Long): Flow<List<PartyEntity>>

    @Query("SELECT * FROM messages WHERE requestId = :id ORDER BY id")
    fun messagesOf(id: Long): Flow<List<MessageEntity>>

    /** پیام‌های همهٔ نسخه‌های یک درخواست (اصلی، اصلاحیه‌ها، لغو) */
    @Query("SELECT m.* FROM messages m JOIN requests r ON r.id = m.requestId WHERE r.rootId = :rootId ORDER BY m.createdAt, m.id")
    fun messagesOfRoot(rootId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE phone = :phone ORDER BY createdAt DESC, id DESC")
    fun messagesOfPhone(phone: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    fun messagesBetween(from: Long, to: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    suspend fun messagesBetweenOnce(from: Long, to: Long): List<MessageEntity>

    // ارسال
    @Query("SELECT * FROM messages WHERE requestId = :rid AND status = 'PENDING' ORDER BY id")
    suspend fun pendingOf(rid: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun messageOnce(id: Long): MessageEntity?

    @Query("UPDATE messages SET status = :s, parts = :parts, sentOk = 0, deliveredOk = 0, failed = 0, updatedAt = :t WHERE id = :id")
    suspend fun markSending(id: Long, s: String, parts: Int, t: Long)

    @Query("UPDATE messages SET status = 'FAILED', failed = failed + 1, updatedAt = :t WHERE id = :id")
    suspend fun markFailedNow(id: Long, t: Long)

    @Query("UPDATE messages SET sentOk = sentOk + 1, updatedAt = :t WHERE id = :id")
    suspend fun incSent(id: Long, t: Long)

    @Query("UPDATE messages SET failed = failed + 1, updatedAt = :t WHERE id = :id")
    suspend fun incFailed(id: Long, t: Long)

    @Query("UPDATE messages SET deliveredOk = deliveredOk + 1, updatedAt = :t WHERE id = :id")
    suspend fun incDelivered(id: Long, t: Long)

    @Query(
        "UPDATE messages SET status = CASE " +
            "WHEN status = 'SELF' THEN status " +
            "WHEN failed > 0 THEN 'FAILED' " +
            "WHEN parts > 0 AND deliveredOk >= parts THEN 'DELIVERED' " +
            "WHEN parts > 0 AND sentOk >= parts THEN 'SENT' " +
            "ELSE status END WHERE id = :id"
    )
    suspend fun recomputeStatus(id: Long)

    @Query("UPDATE messages SET status = 'PENDING', parts = 0, sentOk = 0, deliveredOk = 0, failed = 0, updatedAt = :t WHERE requestId = :rid AND status IN ('FAILED','SENDING')")
    suspend fun resetFailed(rid: Long, t: Long)

    /** پیام‌هایی که از اجرای قبلی در حالت «در حال ارسال» مانده‌اند (مثلاً برنامه بسته شده) */
    @Query("UPDATE messages SET status = 'FAILED' WHERE status IN ('SENDING') AND updatedAt < :before")
    suspend fun failStale(before: Long)
}

@Database(
    entities = [ContactEntity::class, RequestEntity::class, PartyEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "goldpayamak.db")
                .build().also { inst = it }
        }
    }
}
