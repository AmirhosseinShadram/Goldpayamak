package ir.goldpayamak.app.data

import android.content.Context
import androidx.room.withTransaction
import ir.goldpayamak.core.Kind
import ir.goldpayamak.core.Party
import ir.goldpayamak.core.Planned
import ir.goldpayamak.core.Planner
import ir.goldpayamak.core.Ring

class Repo(private val ctx: Context) {
    private val db = AppDb.get(ctx)
    val dao = db.dao()

    private fun List<PartyEntity>.toRing() = sortedBy { it.position }.map { Party(it.name, it.phone) }

    suspend fun ringOf(requestId: Long): List<Party> = dao.partiesOnce(requestId).toRing()

    /** درخواست جدید (اصلی) */
    suspend fun createOriginal(userName: String, ring: List<Party>, price: Long): Long =
        save(Kind.ORIGINAL, null, userName, ring, price, Planner.original(ring, price))

    /** اصلاحیه روی آخرین نسخهٔ فعال */
    suspend fun createCorrection(parentId: Long, userName: String, newRing: List<Party>, newPrice: Long): Long {
        val parent = dao.requestOnce(parentId) ?: error("درخواست پیدا نشد")
        check(parent.status == Status.REQ_ACTIVE) { "فقط آخرین نسخهٔ فعال قابل اصلاح است" }
        val oldRing = ringOf(parentId)
        val plan = Planner.correction(oldRing, parent.price, newRing, newPrice)
        return save(Kind.CORRECTION, parent, userName, newRing, newPrice, plan)
    }

    suspend fun createCancel(parentId: Long, userName: String): Long {
        val parent = dao.requestOnce(parentId) ?: error("درخواست پیدا نشد")
        check(parent.status == Status.REQ_ACTIVE) { "فقط آخرین نسخهٔ فعال قابل لغو است" }
        val ring = ringOf(parentId)
        return save(Kind.CANCEL, parent, userName, ring, parent.price, Planner.cancel(ring, parent.price))
    }

    private suspend fun save(
        kind: Kind, parent: RequestEntity?, userName: String, ring: List<Party>, price: Long, plan: List<Planned>,
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val status = if (kind == Kind.CANCEL) Status.REQ_RECORD else Status.REQ_ACTIVE
        val id = dao.insertRequest(
            RequestEntity(rootId = parent?.rootId ?: 0, parentId = parent?.id, kind = kind.name, price = price,
                userName = userName, createdAt = now, status = status)
        )
        if (parent == null) dao.setRoot(id, id)
        dao.insertParties(ring.mapIndexed { i, p ->
            PartyEntity(requestId = id, position = i, name = p.name, phone = p.phone, isSelf = i == Ring.USER_INDEX)
        })
        dao.insertMessages(plan.map {
            MessageEntity(
                requestId = id, name = it.party.name, phone = it.party.phone, kind = it.kind.name, text = it.text,
                price = price, status = if (it.isSelf) Status.SELF else Status.PENDING, createdAt = now, updatedAt = now,
            )
        })
        if (parent != null) {
            dao.setRequestStatus(parent.id, if (kind == Kind.CANCEL) Status.REQ_CANCELLED else Status.REQ_SUPERSEDED)
        }
        // طرف‌های جدید خودکار در مخاطبین برنامه ذخیره می‌شوند (اگر شمارهٔ تکراری نباشد)
        ring.forEachIndexed { i, p ->
            if (i != Ring.USER_INDEX) dao.insertContactIgnore(ContactEntity(name = p.name, phone = p.phone))
        }
        id
    }
}
