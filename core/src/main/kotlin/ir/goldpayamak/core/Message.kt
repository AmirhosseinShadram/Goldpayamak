package ir.goldpayamak.core

data class Party(val name: String, val phone: String)

enum class Kind { ORIGINAL, CORRECTION, CANCEL }

data class Planned(val party: Party, val isSelf: Boolean, val kind: Kind, val text: String, val transferAmount: TransferAmount? = null)

/**
 * حلقهٔ طرف‌ها: [طرف۱، کاربر(طرف۲)، طرف۳، طرف۴، ...]
 * هر نفر: «از قبلی بگیر، بده به بعدی، با واشر بقیه (از بعد از نفر بعدی به ترتیب)».
 */
object Ring {
    const val USER_INDEX = 1
    const val MIN_PARTIES = 3
    const val MAX_PARTIES = 10

    // قالب‌ها در یک جا ثابت نگه داشته شده‌اند تا راحت تغییر کنند.
    const val RECIPIENT_TEMPLATE = "{name}\nاز {from} بگیر بده به {to}"
    const val SELF_TEMPLATE = "{name}\nاز {from} گرفتم دادم به {to}"
    const val WASHERS_TEMPLATE = " با واشر {list}"
    const val AND = " و "

    fun sentence(ring: List<Party>, index: Int): String {
        val n = ring.size
        require(n >= MIN_PARTIES) { "حداقل ۳ نفر لازم است" }
        require(index in 0 until n)
        val from = ring[(index - 1 + n) % n].name
        val to = ring[(index + 1) % n].name
        val washers = (2..n - 2).map { ring[(index + it) % n].name }
        val template = if (index == USER_INDEX) SELF_TEMPLATE else RECIPIENT_TEMPLATE
        val base = template.replace("{name}", ring[index].name).replace("{from}", from).replace("{to}", to)
        return if (washers.isEmpty()) base
        else base + WASHERS_TEMPLATE.replace("{list}", washers.joinToString(AND))
    }
}

object MessageText {
    const val CORRECTION_PREFIX = "\u2757\u2757 اصلاحیه" // ❗❗
    const val NOTE_LABEL = "توضیحات : "
    const val CANCEL_PREFIX = "\u274C\u274C کنسلی" // ❌❌

    /**
     * پیشوند اصلاحیه یا کنسلی، نام در خط جدا، جملهٔ حواله، یک خط خالی،
     * توضیحات اختیاری و سپس مقدار حواله با واحد آن
     */
    fun body(kind: Kind, sentence: String, amount: TransferAmount, note: String = ""): String = buildString {
        appendPrefix(kind)
        append(sentence).append("\n\n")
        if (kind != Kind.ORIGINAL && note.isNotBlank()) append(NOTE_LABEL).append(note.trim()).append('\n')
        append(amount.format())
    }

    internal fun StringBuilder.appendPrefix(kind: Kind) {
        when (kind) {
            Kind.CORRECTION -> append(CORRECTION_PREFIX).append('\n')
            Kind.CANCEL -> append(CANCEL_PREFIX).append('\n')
            Kind.ORIGINAL -> return
        }
    }
}

object Planner {
    private fun build(kind: Kind, ring: List<Party>, amount: TransferAmount, note: String = ""): List<Planned> =
        ring.mapIndexed { i, p ->
            Planned(p, i == Ring.USER_INDEX, kind, MessageText.body(kind, Ring.sentence(ring, i), amount, note), amount)
        }

    fun original(ring: List<Party>, amount: TransferAmount) =
        build(Kind.ORIGINAL, ring, amount)

    fun cancel(ring: List<Party>, amount: TransferAmount, note: String = "") =
        build(Kind.CANCEL, ring, amount, note)

    /**
     * اصلاحیه: همهٔ طرف‌های حلقهٔ جدید متن جدید می‌گیرند (قدیمی‌ها «اصلاحیه»، تازه‌واردها پیام اصلی)،
     * و کسانی که حذف شده‌اند پیام «کنسلی» با متن قبلی خودشان می‌گیرند.
     */
    fun correction(oldRing: List<Party>, oldAmount: TransferAmount, newRing: List<Party>, newAmount: TransferAmount, note: String = ""): List<Planned> {
        val oldPhones = oldRing.filterIndexed { i, _ -> i != Ring.USER_INDEX }.map { it.phone }.toSet()
        val newPhones = newRing.filterIndexed { i, _ -> i != Ring.USER_INDEX }.map { it.phone }.toSet()
        val result = ArrayList<Planned>()
        newRing.forEachIndexed { i, p ->
            val self = i == Ring.USER_INDEX
            val kind = if (self || p.phone in oldPhones) Kind.CORRECTION else Kind.ORIGINAL
            result += Planned(p, self, kind, MessageText.body(kind, Ring.sentence(newRing, i), newAmount, if (kind == Kind.CORRECTION) note else ""), newAmount)
        }
        oldRing.forEachIndexed { i, p ->
            if (i != Ring.USER_INDEX && p.phone !in newPhones) {
                result += Planned(p, false, Kind.CANCEL, MessageText.body(Kind.CANCEL, Ring.sentence(oldRing, i), oldAmount, note), oldAmount)
            }
        }
        return result
    }

    /** خطاهای فرم؛ لیست خالی یعنی معتبر. ring باید شامل کاربر در اندیس ۱ باشد. */
    fun validate(ring: List<Party>): List<String> {
        val errors = ArrayList<String>()
        if (ring.size < Ring.MIN_PARTIES) errors += "حداقل ۳ نفر لازم است"
        if (ring.size > Ring.MAX_PARTIES) errors += "حداکثر ${Ring.MAX_PARTIES} نفر مجاز است"
        val others = ring.filterIndexed { i, _ -> i != Ring.USER_INDEX }
        if (others.any { it.name.isBlank() }) errors += "نام همهٔ طرف‌ها را وارد کنید"
        if (others.any { Phone.normalize(it.phone) == null }) errors += "شمارهٔ همهٔ طرف‌ها باید موبایل معتبر باشد"
        else if (others.map { Phone.normalize(it.phone) }.toSet().size != others.size) errors += "یک شماره نمی‌تواند دو بار تکرار شود"
        return errors
    }
}
