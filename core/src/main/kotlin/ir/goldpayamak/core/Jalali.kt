package ir.goldpayamak.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class JDate(val y: Int, val m: Int, val d: Int) {
    fun format(persian: Boolean = true): String {
        val s = "%04d/%02d/%02d".format(y, m, d)
        return if (persian) Digits.toPersian(s) else s
    }
}

object Jalali {
    val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    fun fromGregorian(gy: Int, gm: Int, gd: Int): JDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31; jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30
        }
        return JDate(jy, jm, jd)
    }

    fun toGregorian(jy0: Int, jm: Int, jd: Int): LocalDate {
        val jy = jy0 + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + jd +
            (if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]; gm++
        }
        return LocalDate.of(gy, gm, gd)
    }

    fun fromLocalDate(d: LocalDate) = fromGregorian(d.year, d.monthValue, d.dayOfMonth)

    fun fromMillis(ms: Long, zone: ZoneId = TEHRAN): JDate =
        fromLocalDate(java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())

    fun timeOf(ms: Long, zone: ZoneId = TEHRAN, persian: Boolean = true): String {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        val s = "%02d:%02d".format(t.hour, t.minute)
        return if (persian) Digits.toPersian(s) else s
    }

    /** «۱۴۰۵/۰۹/۰۱» یا با ارقام لاتین و جداکنندهٔ - یا /. نامعتبر => null. */
    fun parse(input: String): JDate? {
        val parts = Digits.toLatin(input).trim().split('/', '-', '.')
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y !in 1300..1500 || m !in 1..12 || d < 1) return null
        val max = if (m <= 6) 31 else if (m <= 11) 30 else if (isLeap(y)) 30 else 29
        if (d > max) return null
        return JDate(y, m, d)
    }

    fun isLeap(jy: Int): Boolean {
        // بر اساس رفت‌وبرگشت: ۳۰ اسفند وجود دارد اگر فردا (۱ فروردین سال بعد) یک روز بعد از آن باشد
        val a = toGregorian(jy, 12, 29)
        val b = toGregorian(jy + 1, 1, 1)
        return a.plusDays(2) == b
    }

    /** ابتدای روز (به وقت تهران) به میلی‌ثانیه */
    fun startOfDayMillis(d: JDate, zone: ZoneId = TEHRAN): Long =
        toGregorian(d.y, d.m, d.d).atStartOfDay(zone).toInstant().toEpochMilli()

    /** پایان روز (آخرین میلی‌ثانیه) */
    fun endOfDayMillis(d: JDate, zone: ZoneId = TEHRAN): Long =
        toGregorian(d.y, d.m, d.d).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun today(zone: ZoneId = TEHRAN): JDate = fromLocalDate(LocalDate.now(zone))

    @Suppress("unused")
    fun nowDateTime(zone: ZoneId = TEHRAN): LocalDateTime = LocalDateTime.now(zone)
}
