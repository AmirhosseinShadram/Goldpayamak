package ir.goldpayamak.core

enum class GoldSide(val word: String) { SELL("فروختم"), BUY("خریدم") }

/** درخواست «طلایی»: پیامک به یک نفر. مبلغ و فی طلایی هر دو قیمت‌اند (سه رقم سه رقم جدا می‌شوند). */
data class GoldData(val party: Party, val side: GoldSide, val amount: Long, val rate: Long)

object Gold {
    // قالب ثابت: «بابت {نام کاربر} {فروختم|خریدم} به {نام گیرنده} {مبلغ} در فی طلایی {فی}»
    const val TEMPLATE = "بابت {user} {side} به {to} {amount} در فی طلایی {rate}"

    fun sentence(userName: String, d: GoldData): String = TEMPLATE
        .replace("{user}", userName)
        .replace("{side}", d.side.word)
        .replace("{to}", d.party.name)
        .replace("{amount}", Price.formatToman(d.amount))
        .replace("{rate}", Price.formatToman(d.rate))

    fun body(userName: String, kind: Kind, d: GoldData, note: String = ""): String = buildString {
        with(MessageText) { prefixAndNote(kind, note) }
        append(sentence(userName, d))
    }

    private fun planned(userName: String, kind: Kind, d: GoldData, note: String = "") =
        Planned(d.party, false, kind, body(userName, kind, d, note))

    fun original(userName: String, d: GoldData) = listOf(planned(userName, Kind.ORIGINAL, d))

    fun cancel(userName: String, old: GoldData, note: String = "") = listOf(planned(userName, Kind.CANCEL, old, note))

    /** اصلاحیه: اگر گیرنده همان باشد یک پیام اصلاحیه؛ اگر گیرنده عوض شده، گیرندهٔ قبلی «کنسلی» و گیرندهٔ جدید پیام اصلی می‌گیرد. */
    fun correction(userName: String, old: GoldData, new: GoldData, note: String = ""): List<Planned> =
        if (old.party.phone == new.party.phone) listOf(planned(userName, Kind.CORRECTION, new, note))
        else listOf(planned(userName, Kind.ORIGINAL, new), planned(userName, Kind.CANCEL, old, note))

    fun validate(d: GoldData): List<String> {
        val e = ArrayList<String>()
        if (d.party.name.isBlank()) e += "نام گیرنده را وارد کنید"
        if (Phone.normalize(d.party.phone) == null) e += "شمارهٔ گیرنده باید موبایل معتبر باشد"
        if (d.amount <= 0) e += "مبلغ را وارد کنید"
        if (d.rate <= 0) e += "فی طلایی را وارد کنید"
        return e
    }
}
