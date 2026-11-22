package ir.goldpayamak.core

object Csv {
    /** BOM برای اینکه Excel فارسی را درست نشان بدهد. */
    const val BOM = "﻿"

    /** جلوگیری از اجرای فرمول در Excel برای سلول‌هایی که با = + - @ شروع می‌شوند. */
    fun safe(cell: String): String =
        if (cell.isNotEmpty() && cell[0] in "=+-@\t\r") "'$cell" else cell

    fun quote(cell: String): String = "\"" + cell.replace("\"", "\"\"") + "\""

    /** ستون شماره: با ="09..." صفر اول در Excel حفظ می‌شود. فقط برای ارقام معتبر. */
    fun phoneCell(phone: String): String =
        if (phone.isNotEmpty() && phone.all { it.isDigit() }) "=\"$phone\"" else quote(safe(phone))

    fun build(header: List<String>, rows: List<List<String>>): String = buildString {
        append(BOM)
        append(header.joinToString(",") { quote(it) }).append("\r\n")
        for (r in rows) append(r.joinToString(",")).append("\r\n")
    }
}
