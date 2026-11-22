package ir.goldpayamak.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.app.data.Status
import ir.goldpayamak.core.JDate
import ir.goldpayamak.core.Jalali
import java.time.LocalDate

@Composable
fun ReportScreen(vm: AppViewModel) {
    val today = remember { Jalali.today() }
    var fromText by remember { mutableStateOf(JDate(today.y, today.m, 1).format()) }
    var toText by remember { mutableStateOf(today.format()) }
    val from = Jalali.parse(fromText)
    val to = Jalali.parse(toText)
    val valid = from != null && to != null && Jalali.startOfDayMillis(from) <= Jalali.endOfDayMillis(to)
    val fromMs = if (valid) Jalali.startOfDayMillis(from!!) else 0L
    val toMs = if (valid) Jalali.endOfDayMillis(to!!) else -1L
    val msgs by remember(fromMs, toMs) { vm.dao.messagesBetween(fromMs, toMs) }.collectAsState(initial = emptyList())
    var exportMsg by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri, fromMs, toMs) { ok -> exportMsg = if (ok) "فایل ذخیره شد." else "ذخیره فایل ناموفق بود." }
    }

    fun setRange(daysBack: Int?) {
        val t = LocalDate.now(Jalali.TEHRAN)
        if (daysBack == null) { fromText = JDate(today.y, today.m, 1).format(); toText = today.format() }
        else { fromText = Jalali.fromLocalDate(t.minusDays(daysBack.toLong())).format(); toText = Jalali.fromLocalDate(t).format() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromText, { fromText = it }, label = { Text("از تاریخ (شمسی)") }, singleLine = true,
                    isError = from == null, modifier = Modifier.weight(1f))
                OutlinedTextField(toText, { toText = it }, label = { Text("تا تاریخ (شمسی)") }, singleLine = true,
                    isError = to == null, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { setRange(0) }, label = { Text("امروز") })
                AssistChip(onClick = { setRange(6) }, label = { Text("۷ روز") })
                AssistChip(onClick = { setRange(null) }, label = { Text("این ماه") })
                AssistChip(onClick = { setRange(29) }, label = { Text("۳۰ روز") })
            }
        }
        if (!valid) item { Text("تاریخ‌ها را به شکل ۱۴۰۵/۰۹/۰۱ وارد کنید و تاریخ شروع قبل از پایان باشد.", color = MaterialTheme.colorScheme.error) }
        item {
            val sent = msgs.count { it.status == Status.SENT || it.status == Status.DELIVERED }
            val failed = msgs.count { it.status == Status.FAILED }
            val reqs = msgs.map { it.requestId }.toSet().size
            Text("${ir.goldpayamak.core.Digits.toPersian(msgs.size.toString())} پیام در ${ir.goldpayamak.core.Digits.toPersian(reqs.toString())} ثبت • " +
                "ارسال‌شده ${ir.goldpayamak.core.Digits.toPersian(sent.toString())} • ناموفق ${ir.goldpayamak.core.Digits.toPersian(failed.toString())}",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(enabled = valid && msgs.isNotEmpty(), onClick = {
                export.launch("report-${fromText.replace('/', '-')}_${toText.replace('/', '-')}.csv".let { ir.goldpayamak.core.Digits.toLatin(it) })
            }) { Text("دانلود گزارش (CSV / Excel)") }
            exportMsg?.let { Text(it) }
        }
        items(msgs) { MessageCard(it) }
    }
}
