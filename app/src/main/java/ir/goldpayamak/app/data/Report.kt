package ir.goldpayamak.app.data

/** یک ردیف گزارش: پیام طرف ۲ (ارسال‌نشده) و زیرمجموعه‌اش، پیامک‌های ارسال‌شده به طرف‌ها */
data class ReportGroup(val request: RequestEntity, val self: MessageEntity?, val recipients: List<MessageEntity>)

fun groupReport(requests: List<RequestEntity>, messages: List<MessageEntity>): List<ReportGroup> {
    val byReq = messages.groupBy { it.requestId }
    return requests.map { r ->
        val ms = byReq[r.id].orEmpty()
        ReportGroup(r, ms.firstOrNull { it.status == Status.SELF }, ms.filter { it.status != Status.SELF })
    }
}

/** برچسب وضعیت خود درخواست */
fun requestStateLabel(r: RequestEntity): String = when {
    r.status == Status.REQ_SUPERSEDED -> "اصلاح شده ❗❗"
    r.status == Status.REQ_CANCELLED -> "کنسل شده ❌❌"
    r.kind == "CORRECTION" -> "اصلاحیه ❗❗"
    r.kind == "CANCEL" -> "کنسلی ❌❌"
    else -> "فعال"
}

/** برچسب روی پیامک هر طرف وقتی نسخهٔ بعدی (اصلاحیه/کنسلی) آمده باشد */
fun supersededTag(r: RequestEntity): String? = when (r.status) {
    Status.REQ_SUPERSEDED -> "اصلاح شده ❗❗"
    Status.REQ_CANCELLED -> "کنسل شده ❌❌"
    else -> null
}
