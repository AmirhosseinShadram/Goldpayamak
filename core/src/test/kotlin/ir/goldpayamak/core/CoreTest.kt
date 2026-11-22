package ir.goldpayamak.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private fun ring(n: Int): List<Party> {
        // ring[1] = کاربر (طرف ۲)
        val names = (1..n).map { "P$it" }
        return names.map { Party(it, "") }
    }

    @Test fun fivePartyRingMatchesSpec() {
        val r = ring(5)
        assertEquals("P1 از P5 بگیر بده به P2 با واشر P3 و P4", Ring.sentence(r, 0))
        assertEquals("P2 از P1 گرفتم دادم به P3 با واشر P4 و P5", Ring.sentence(r, 1)) // خود کاربر
        assertEquals("P3 از P2 بگیر بده به P4 با واشر P5 و P1", Ring.sentence(r, 2))
        assertEquals("P4 از P3 بگیر بده به P5 با واشر P1 و P2", Ring.sentence(r, 3))
        assertEquals("P5 از P4 بگیر بده به P1 با واشر P2 و P3", Ring.sentence(r, 4))
    }

    @Test fun threePartyHasNoWashers() {
        val r = ring(3)
        assertEquals("P1 از P3 بگیر بده به P2", Ring.sentence(r, 0))
        assertEquals("P2 از P1 گرفتم دادم به P3", Ring.sentence(r, 1))
        assertEquals("P3 از P2 بگیر بده به P1", Ring.sentence(r, 2))
    }

    @Test fun sixPartyAddsWasher() {
        val r = ring(6)
        assertEquals("P2 از P1 گرفتم دادم به P3 با واشر P4 و P5 و P6", Ring.sentence(r, 1))
        assertEquals("P1 از P6 بگیر بده به P2 با واشر P3 و P4 و P5", Ring.sentence(r, 0))
    }

    @Test fun bodyLayout() {
        assertEquals("جمله\n\n۱۲۰,۰۰۰", MessageText.body(Kind.ORIGINAL, "جمله", 120000))
        assertEquals("اصلاحیه:\nجمله\n\n۱۲۰,۰۰۰", MessageText.body(Kind.CORRECTION, "جمله", 120000))
        assertEquals("لغو:\nجمله\n\n۱۲۰,۰۰۰", MessageText.body(Kind.CANCEL, "جمله", 120000))
    }

    @Test fun priceFormatAndParse() {
        assertEquals("۱", Price.format(1))
        assertEquals("۹۹۹", Price.format(999))
        assertEquals("۱,۰۰۰", Price.format(1000))
        assertEquals("۱۲۰,۰۰۰", Price.format(120000))
        assertEquals("۱,۲۳۴,۵۶۷", Price.format(1234567))
        assertEquals(120000L, Price.parse("۱۲۰۰۰۰"))
        assertEquals(120000L, Price.parse("١٢٠٬٠٠٠"))
        assertEquals(120000L, Price.parse("120,000"))
        assertNull(Price.parse(""))
        assertNull(Price.parse("0"))
        assertNull(Price.parse("12a"))
        assertNull(Price.parse("-5"))
    }

    @Test fun phoneNormalize() {
        assertEquals("09123456789", Phone.normalize("09123456789"))
        assertEquals("09123456789", Phone.normalize("+98 912 345 6789"))
        assertEquals("09123456789", Phone.normalize("0098-912-345-6789"))
        assertEquals("09123456789", Phone.normalize("۰۹۱۲۳۴۵۶۷۸۹"))
        assertEquals("09123456789", Phone.normalize("9123456789"))
        assertNull(Phone.normalize("02112345678"))
        assertNull(Phone.normalize("0912"))
    }

    @Test fun jalaliKnownDates() {
        assertEquals(JDate(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(JDate(1404, 1, 1), Jalali.fromGregorian(2025, 3, 21))
        assertEquals(JDate(1405, 9, 1), Jalali.fromGregorian(2026, 11, 22))
        assertEquals(LocalDate.of(2026, 11, 22), Jalali.toGregorian(1405, 9, 1))
        assertEquals(LocalDate.of(2024, 3, 20), Jalali.toGregorian(1403, 1, 1))
        assertEquals("۱۴۰۵/۰۹/۰۱", JDate(1405, 9, 1).format())
    }

    @Test fun jalaliRoundTrip() {
        var d = LocalDate.of(2018, 1, 1)
        while (d.isBefore(LocalDate.of(2032, 1, 1))) {
            val j = Jalali.fromLocalDate(d)
            assertEquals(d, Jalali.toGregorian(j.y, j.m, j.d), "round trip $d -> $j")
            d = d.plusDays(1)
        }
    }

    @Test fun jalaliLeapAndParse() {
        assertTrue(Jalali.isLeap(1403))
        assertTrue(!Jalali.isLeap(1404))
        assertEquals(JDate(1405, 9, 1), Jalali.parse("۱۴۰۵/۰۹/۰۱"))
        assertEquals(JDate(1403, 12, 30), Jalali.parse("1403-12-30"))
        assertNull(Jalali.parse("1404/12/30"))
        assertNull(Jalali.parse("1404/13/01"))
        assertNull(Jalali.parse("abc"))
        val s = Jalali.startOfDayMillis(JDate(1405, 9, 1))
        val e = Jalali.endOfDayMillis(JDate(1405, 9, 1))
        assertEquals(24L * 3600 * 1000 - 1, e - s)
    }

    @Test fun correctionPlan() {
        val old = listOf(Party("A", "0911"), Party("Me", ""), Party("C", "0913"), Party("D", "0914"))
        // D حذف، E اضافه
        val new = listOf(Party("A", "0911"), Party("Me", ""), Party("C", "0913"), Party("E", "0915"))
        val plan = Planner.correction(old, 100, new, 200)
        val byPhone = plan.associateBy { it.party.phone + it.kind }
        assertEquals(Kind.CORRECTION, byPhone["0911CORRECTION"]!!.kind)
        assertEquals(Kind.ORIGINAL, byPhone["0915ORIGINAL"]!!.kind)
        val cancel = byPhone["0914CANCEL"]!!
        assertTrue(cancel.text.contains("۱۰۰"))
        assertTrue(cancel.text.startsWith("لغو:\nD از C بگیر"))
        assertEquals(5, plan.size) // ۴ نفر جدید (شامل خود کاربر) + ۱ لغو
        assertTrue(plan.any { it.isSelf })
    }

    @Test fun cancelPlan() {
        val r = listOf(Party("A", "0911"), Party("Me", ""), Party("C", "0913"))
        val plan = Planner.cancel(r, 5000)
        assertEquals(3, plan.size)
        assertTrue(plan.all { it.kind == Kind.CANCEL })
    }

    @Test fun validation() {
        val ok = listOf(Party("A", "09111111111"), Party("Me", ""), Party("C", "09122222222"))
        assertEquals(emptyList(), Planner.validate(ok))
        val dup = listOf(Party("A", "09111111111"), Party("Me", ""), Party("C", "+989111111111"))
        assertTrue(Planner.validate(dup).isNotEmpty())
        assertTrue(Planner.validate(listOf(Party("A", "09111111111"), Party("Me", ""))).isNotEmpty())
    }
}
