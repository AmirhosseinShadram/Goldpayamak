package ir.goldpayamak.core

/** تبدیل ارقام فارسی/عربی به لاتین و برعکس، و قالب‌بندی قیمت. */
object Digits {
    fun toLatin(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }

    fun toPersian(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c)
    }
}

object Price {
    private val separators = Regex("[,٬،\\s‌‏‎]")

    /** قیمت ورودی (با هر نوع رقم و جداکننده) را به عدد تبدیل می‌کند؛ نامعتبر یا صفر => null. */
    fun parse(input: String): Long? {
        val t = separators.replace(Digits.toLatin(input), "")
        if (t.isEmpty() || t.length > 12 || !t.all { it in '0'..'9' }) return null
        val v = t.toLong()
        return if (v > 0) v else null
    }

    /** قیمت همراه واحد: ۱۲۰,۰۰۰ تومان */
    fun formatToman(value: Long): String = format(value) + " تومان"

    /** سه رقم سه رقم از راست جدا می‌شود، با ارقام فارسی و بدون واحد: ۱۲۰,۰۰۰ */
    fun format(value: Long): String {
        val s = value.toString()
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return Digits.toPersian(sb.toString())
    }
}

object Phone {
    /** شماره موبایل ایران را به شکل 09xxxxxxxxx برمی‌گرداند؛ نامعتبر => null. */
    fun normalize(input: String): String? {
        var t = Digits.toLatin(input).filter { it.isDigit() || it == '+' }
        if (t.startsWith("+")) t = t.substring(1)
        if (t.startsWith("0098")) t = t.substring(4)
        else if (t.startsWith("98") && t.length == 12) t = t.substring(2)
        if (t.length == 10 && t.startsWith("9")) t = "0$t"
        return if (Regex("^09\\d{9}$").matches(t)) t else null
    }
}

object TextNorm {
    private val directionMarks = Regex("[\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]")
    private val thousandsSeparators = Regex("(?<=[0-9])[,٬، \u00A0\u202F]+(?=[0-9])")

    fun history(value: String): String =
        fa(value).replace(directionMarks, "").replace(thousandsSeparators, "").replace('٫', '.').trim()

    /** برای جستجو: ی/ک عربی به فارسی، ارقام به لاتین، حروف کوچک */
    fun fa(s: String): String = Digits.toLatin(s).replace('\u064A', '\u06CC').replace('\u0643', '\u06A9').lowercase().trim()
}
