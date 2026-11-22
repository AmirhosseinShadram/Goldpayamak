package ir.goldpayamak.core

data class Party(val name: String, val phone: String)

enum class Kind { ORIGINAL, CORRECTION, CANCEL }

data class Planned(val party: Party, val isSelf: Boolean, val kind: Kind, val text: String)

/**
 * حلقهٔ طرف‌ها: [طرف۱، کاربر(طرف۲)، طرف۳، طرف۴، ...]
 * هر نفر: «از قبلی بگیر، بده به بعدی، با واشر بقیه (از بعد از نفر بعدی به ترتیب)».
 */
object Ring {
    const val USER_INDEX = 1
    const val MIN_PARTIES = 3
    const val MAX_PARTIES = 10

    // قالب‌ها در یک جا ثابت نگه داشته شده‌اند تا راحت تغییر کنند.
    const val RECIPIENT_TEMPLATE = "از {from} بگیر بده به {to}"
    const val SELF_TEMPLATE = "از {from} گرفتم دادم به {to}"
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
        val base = template.replace("{from}", from).replace("{to}", to)
        return if (washers.isEmpty()) base
        else base + WASHERS_TEMPLATE.replace("{list}", washers.joinToString(AND))
    }
}

object MessageText {
    const val CORRECTION_PREFIX = "اصلاحیه:"
    const val CANCEL_PREFIX = "لغو:"

    /** نام کاربر، (پیشوند)، یک خط خالی، جمله، یک خط خالی، قیمت */
    fun body(userName: String, kind: Kind, sentence: String, price: Long): String = buildString {
        append(userName)
        when (kind) {
            Kind.CORRECTION -> append('\n').append(CORRECTION_PREFIX)
            Kind.CANCEL -> append('\n').append(CANCEL_PREFIX)
            Kind.ORIGINAL -> {}
        }
        append("\n\n").append(sentence).append("\n\n").append(Price.format(price))
    }
}

object Planner {
    private fun build(userName: String, kind: Kind, ring: List<Party>, price: Long, skip: (Party, Int) -> Boolean = { _, _ -> false }): List<Planned> =
        ring.mapIndexedNotNull { i, p ->
            if (skip(p, i)) null
            else Planned(p, i == Ring.USER_INDEX, kind, MessageText.body(userName, kind, Ring.sentence(ring, i), price))
        }

    fun original(userName: String, ring: List<Party>, price: Long) =
        build(userName, Kind.ORIGINAL, ring, price)

    fun cancel(userName: String, ring: List<Party>, price: Long) =
        build(userName, Kind.CANCEL, ring, price)

    /**
     * اصلاحیه: همهٔ طرف‌های حلقهٔ جدید متن جدید می‌گیرند (قدیمی‌ها «اصلاحیه»، تازه‌واردها پیام اصلی)،
     * و کسانی که حذف شده‌اند پیام «لغو» با متن قبلی خودشان می‌گیرند.
     */
    fun correction(userName: String, oldRing: List<Party>, oldPrice: Long, newRing: List<Party>, newPrice: Long): List<Planned> {
        val oldPhones = oldRing.filterIndexed { i, _ -> i != Ring.USER_INDEX }.map { it.phone }.toSet()
        val newPhones = newRing.filterIndexed { i, _ -> i != Ring.USER_INDEX }.map { it.phone }.toSet()
        val result = ArrayList<Planned>()
        newRing.forEachIndexed { i, p ->
            val self = i == Ring.USER_INDEX
            val kind = if (self || p.phone in oldPhones) Kind.CORRECTION else Kind.ORIGINAL
            result += Planned(p, self, kind, MessageText.body(userName, kind, Ring.sentence(newRing, i), newPrice))
        }
        oldRing.forEachIndexed { i, p ->
            if (i != Ring.USER_INDEX && p.phone !in newPhones) {
                result += Planned(p, false, Kind.CANCEL, MessageText.body(userName, Kind.CANCEL, Ring.sentence(oldRing, i), oldPrice))
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
