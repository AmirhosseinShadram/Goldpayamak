package ir.goldpayamak.app.data

import android.content.Context
import androidx.room.withTransaction
import ir.goldpayamak.core.Gold
import ir.goldpayamak.core.GoldData
import ir.goldpayamak.core.GoldSide
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
    suspend fun createCorrection(parentId: Long, userName: String, newRing: List<Party>, newPrice: Long, note: String = ""): Long {
        val parent = dao.requestOnce(parentId) ?: error("درخواست پیدا نشد")
        check(parent.status == Status.REQ_ACTIVE) { "فقط آخرین نسخهٔ فعال قابل اصلاح است" }
        val oldRing = ringOf(parentId)
        val plan = Planner.correction(oldRing, parent.price, newRing, newPrice, note)
        return save(Kind.CORRECTION, parent, userName, newRing, newPrice, plan, note = note.trim())
    }

    suspend fun goldOf(requestId: Long): GoldData {
        val r = dao.requestOnce(requestId) ?: error("درخواست پیدا نشد")
        val party = ringOf(requestId).first()
        return GoldData(party, GoldSide.valueOf(r.side), r.price, r.rate)
    }

    suspend fun createGold(userName: String, d: GoldData): Long =
        save(Kind.ORIGINAL, null, userName, listOf(d.party), d.amount, Gold.original(userName, d), "GOLD", d.rate, d.side.name)

    suspend fun createGoldCorrection(parentId: Long, userName: String, new: GoldData, note: String = ""): Long {
        val parent = dao.requestOnce(parentId) ?: error("درخواست پیدا نشد")
        check(parent.status == Status.REQ_ACTIVE) { "فقط آخرین نسخهٔ فعال قابل اصلاح است" }
        val old = goldOf(parentId)
        return save(Kind.CORRECTION, parent, userName, listOf(new.party), new.amount,
            Gold.correction(userName, old, new, note), "GOLD", new.rate, new.side.name, note.trim())
    }

    suspend fun createCancel(parentId: Long, userName: String, note: String = ""): Long {
        val parent = dao.requestOnce(parentId) ?: error("درخواست پیدا نشد")
        check(parent.status == Status.REQ_ACTIVE) { "فقط آخرین نسخهٔ فعال قابل کنسلی است" }
        if (parent.type == "GOLD") {
            val old = goldOf(parentId)
            return save(Kind.CANCEL, parent, userName, listOf(old.party), old.amount, Gold.cancel(userName, old, note), "GOLD", old.rate, old.side.name, note.trim())
        }
        val ring = ringOf(parentId)
        return save(Kind.CANCEL, parent, userName, ring, parent.price, Planner.cancel(ring, parent.price, note), note = note.trim())
    }

    private suspend fun save(
        kind: Kind, parent: RequestEntity?, userName: String, ring: List<Party>, price: Long, plan: List<Planned>,
        type: String = "RING", rate: Long = 0, side: String = "", note: String = "",
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val status = if (kind == Kind.CANCEL) Status.REQ_RECORD else Status.REQ_ACTIVE
        val id = dao.insertRequest(
            RequestEntity(rootId = parent?.rootId ?: 0, parentId = parent?.id, kind = kind.name, price = price,
                userName = userName, createdAt = now, status = status, type = type, rate = rate, side = side, note = note)
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
