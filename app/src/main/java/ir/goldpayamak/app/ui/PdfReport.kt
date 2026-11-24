package ir.goldpayamak.app.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import ir.goldpayamak.app.data.ReportGroup
import ir.goldpayamak.app.data.requestStateLabel
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Digits
import ir.goldpayamak.core.Jalali
import java.io.OutputStream

/** گزارش PDF راست‌به‌چپ: هر ردیف پیام طرف ۲ است و زیرش پیامک‌های ارسال‌شده به طرف‌ها با وضعیت. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 36

    fun write(out: OutputStream, fromText: String, toText: String, groups: List<ReportGroup>, reportTitle: String) {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var y = M.toFloat()

        val normal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val bold = TextPaint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = TextPaint(bold).apply { textSize = 18f }
        val small = TextPaint(normal).apply { textSize = 9.5f; color = Color.DKGRAY }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M.toFloat()
        }

        fun layout(text: String, paint: TextPaint, width: Int) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.2f)
                .build()

        fun block(text: String, paint: TextPaint, indent: Int = 0, gap: Float = 4f) {
            val l = layout(text, paint, W - 2 * M - indent)
            if (y + l.height > H - M) newPage()
            page.canvas.save()
            page.canvas.translate(M.toFloat(), y)
            l.draw(page.canvas)
            page.canvas.restore()
            y += l.height + gap
        }

        fun needSpace(h: Float) { if (y + h > H - M) newPage() }

        val ok = Digits::toPersian
        block(reportTitle, title, gap = 6f)
        block("از تاریخ ${ok(fromText)} تا ${ok(toText)}", normal)
        if (groups.isEmpty()) block("در این بازه موردی ثبت نشده است.", normal)

        for (g in groups) {
            needSpace(110f)
            page.canvas.drawLine(M.toFloat(), y, (W - M).toFloat(), y, line)
            y += 8f
            val r = g.request
            val isGold = r.type == "GOLD"
            block("${Jalali.fromMillis(r.createdAt).format()}  ${Jalali.timeOf(r.createdAt)} • ${plain(requestStateLabel(r))}", small)
            block("کاربر: ${r.userName}", small)
            block(plain(g.self?.text ?: g.recipients.firstOrNull()?.text ?: "(بدون متن)"), bold, gap = 6f)
            val shownText = g.self?.text ?: g.recipients.firstOrNull()?.text.orEmpty()
            if (r.note.isNotBlank() && !shownText.contains(r.note)) block("توضیحات: ${r.note}", normal, gap = 6f)
            for (m in g.recipients) {
                val st = plain(statusLabel(m.status))
                val amount = if (!isGold && m.transferAmount != r.transferAmount) "  —  ${m.transferAmount.format()}" else ""
                block("${m.name}  ${m.phone}$amount  —  $st\nکاربر: ${r.userName}", normal, indent = 16, gap = 2f)
            }
            if (g.recipients.isEmpty()) block("(پیامکی به طرف‌ها ارسال نشده)", small, indent = 16)
            y += 6f
        }
        doc.finishPage(page)
        try { doc.writeTo(out) } finally { doc.close() }
    }
}
