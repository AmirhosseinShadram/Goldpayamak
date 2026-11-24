package ir.goldpayamak.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ir.goldpayamak.core.TransferAmount

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
    const val REQ_RECORD = "RECORD" // خود ردیف اصلاحیه/کنسلی
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
    /** RING = حواله (حلقهٔ طرف‌ها)، GOLD = طلایی (یک گیرنده) */
    @ColumnInfo(defaultValue = "'RING'") val type: String = "RING",
    /** فقط طلایی: فی طلایی (قیمت دوم)؛ مبلغ در price ذخیره می‌شود */
    @ColumnInfo(defaultValue = "0") val rate: Long = 0,
    /** فقط طلایی: SELL یا BUY */
    @ColumnInfo(defaultValue = "''") val side: String = "",
    /** توضیحات اصلاحیه/کنسلی (در پیامک هم فرستاده می‌شود) */
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val goldGrams: String? = null,
) {
    val transferAmount: TransferAmount
        get() = goldGrams?.let { requireNotNull(TransferAmount.GoldGrams.parse(it)) } ?: TransferAmount.Toman(price)
}

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
    val goldGrams: String? = null,
) {
    val transferAmount: TransferAmount
        get() = goldGrams?.let { requireNotNull(TransferAmount.GoldGrams.parse(it)) } ?: TransferAmount.Toman(price)
}

data class HistorySearchEntry(
    val rootId: Long,
    val name: String,
    val phone: String,
    val text: String,
    val price: Long,
    val goldGrams: String?,
    val rate: Long?,
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

    /** پیامک درخواست‌های طلایی (برای نمایش متن در تاریخچه) */
    @Query("SELECT m.* FROM messages m JOIN requests r ON r.id = m.requestId WHERE r.type = 'GOLD'")
    fun goldMessages(): Flow<List<MessageEntity>>

    /** پیام طرف ۲ (ثبت‌شده، ارسال‌نشده) همهٔ درخواست‌ها برای نمایش در تاریخچه */
    @Query("SELECT * FROM messages WHERE status = 'SELF'")
    fun selfMessages(): Flow<List<MessageEntity>>

    /** فقط پیام اول (ریشهٔ هر درخواست) برای تاریخچه */
    @Query("SELECT * FROM requests WHERE kind = 'ORIGINAL' ORDER BY createdAt DESC, id DESC")
    fun roots(): Flow<List<RequestEntity>>

    /** همهٔ اصلاحیه‌ها و کنسلی‌ها (زیرمجموعهٔ هر ریشه با rootId) */
    @Query("SELECT * FROM requests WHERE kind != 'ORIGINAL' ORDER BY createdAt, id")
    fun actions(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE rootId = :rootId ORDER BY createdAt, id")
    fun chainOf(rootId: Long): Flow<List<RequestEntity>>

    @Query("SELECT COUNT(*) FROM requests WHERE parentId = :id")
    suspend fun childCount(id: Long): Int

    @Query("SELECT * FROM requests WHERE parentId = :id ORDER BY createdAt, id")
    fun children(id: Long): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    fun requestsBetween(from: Long, to: Long): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    suspend fun requestsBetweenOnce(from: Long, to: Long): List<RequestEntity>

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

    /** پیام‌های همهٔ نسخه‌های یک درخواست (اصلی، اصلاحیه‌ها، کنسلی) */
    @Query("SELECT m.* FROM messages m JOIN requests r ON r.id = m.requestId WHERE r.rootId = :rootId ORDER BY m.createdAt, m.id")
    fun messagesOfRoot(rootId: Long): Flow<List<MessageEntity>>

    @Query("""
        SELECT r.rootId, m.name, m.phone, m.text, m.price, m.goldGrams,
            CASE WHEN r.type = 'GOLD' THEN r.rate END AS rate
        FROM messages m JOIN requests r ON r.id = m.requestId
        WHERE r.type = :type
    """)
    fun historySearchEntries(type: String): Flow<List<HistorySearchEntry>>

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
    @Query("UPDATE messages SET status = 'FAILED' WHERE status IN ('SENDING', 'PENDING') AND updatedAt < :before")
    suspend fun failStale(before: Long)
}

private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE requests ADD COLUMN type TEXT NOT NULL DEFAULT 'RING'")
        db.execSQL("ALTER TABLE requests ADD COLUMN rate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE requests ADD COLUMN side TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE requests ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE requests ADD COLUMN goldGrams TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN goldGrams TEXT")
        // پیام کنسلیِ طرف حذف‌شده، مبلغ نسخهٔ پیش از اصلاحیه را دارد.
        db.execSQL("""
            UPDATE messages
            SET price = (
                SELECT parent.price FROM requests AS correction
                JOIN requests AS parent ON parent.id = correction.parentId
                WHERE correction.id = messages.requestId
            )
            WHERE kind = 'CANCEL' AND requestId IN (
                SELECT correction.id FROM requests AS correction
                JOIN requests AS parent ON parent.id = correction.parentId
                WHERE correction.kind = 'CORRECTION' AND correction.type = 'RING'
            )
        """.trimIndent())
    }
}

@Database(
    entities = [ContactEntity::class, RequestEntity::class, PartyEntity::class, MessageEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "goldpayamak.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { inst = it }
        }
    }
}
