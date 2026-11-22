package ir.goldpayamak.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.app.data.*
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Digits
import ir.goldpayamak.core.JDate
import ir.goldpayamak.core.Jalali
import ir.goldpayamak.core.Price
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
    val reqs by remember(fromMs, toMs) { vm.dao.requestsBetween(fromMs, toMs) }.collectAsState(initial = emptyList())
    val msgs by remember(fromMs, toMs) { vm.dao.messagesBetween(fromMs, toMs) }.collectAsState(initial = emptyList())
    var typeTab by remember { mutableIntStateOf(0) } // 0 = حواله، 1 = طلایی
    val typeKey = if (typeTab == 0) "RING" else "GOLD"
    val groups = remember(reqs, msgs, typeKey) { groupReport(reqs, msgs).filter { it.request.type == typeKey } }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val fileSuffix = Digits.toLatin("${fromText}_${toText}").replace('/', '-')

    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportPdf(uri, fromMs, toMs, fromText, toText, typeKey) { ok -> exportMsg = if (ok) "فایل PDF ذخیره شد." else "ذخیرهٔ PDF ناموفق بود." }
    }

    fun setRange(daysBack: Int?) {
        val t = LocalDate.now(Jalali.TEHRAN)
        if (daysBack == null) { fromText = JDate(today.y, today.m, 1).format(); toText = today.format() }
        else { fromText = Jalali.fromLocalDate(t.minusDays(daysBack.toLong())).format(); toText = Jalali.fromLocalDate(t).format() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TabRow(selectedTabIndex = typeTab) {
                Tab(selected = typeTab == 0, onClick = { typeTab = 0 }, text = { Text("گزارش حواله") })
                Tab(selected = typeTab == 1, onClick = { typeTab = 1 }, text = { Text("گزارش طلایی") })
            }
        }
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
            val rec = groups.flatMap { it.recipients }
            val sent = rec.count { it.status == Status.SENT || it.status == Status.DELIVERED }
            val failed = rec.count { it.status == Status.FAILED }
            Text("${Digits.toPersian(groups.size.toString())} ثبت • ${Digits.toPersian(rec.size.toString())} پیامک به طرف‌ها • " +
                "ارسال‌شده ${Digits.toPersian(sent.toString())} • ناموفق ${Digits.toPersian(failed.toString())}",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = valid && groups.isNotEmpty(), onClick = { exportPdf.launch("report-$fileSuffix.pdf") }) { Text("دانلود PDF") }
            }
            exportMsg?.let { Text(it) }
            if (typeTab == 0) Text("روی هر مورد بزنید تا پیامک‌های ارسال‌شده به طرف‌ها و وضعیتشان نمایش داده شود.", style = MaterialTheme.typography.bodySmall)
        }
        items(groups, key = { it.request.id }) { g -> ReportGroupCard(g) }
    }
}

@Composable
private fun ReportGroupCard(g: ReportGroup) {
    var open by remember { mutableStateOf(false) }
    val r = g.request
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${Jalali.fromMillis(r.createdAt).format()}  ${Jalali.timeOf(r.createdAt)}", style = MaterialTheme.typography.labelMedium)
                Text(requestStateLabel(r), style = MaterialTheme.typography.labelMedium, color = requestColor(r))
            }
            Text(plain(g.self?.text ?: g.recipients.firstOrNull()?.text ?: "—"), style = MaterialTheme.typography.bodyMedium)
            if (r.note.isNotBlank()) Text("توضیحات: ${r.note}", style = MaterialTheme.typography.bodySmall)
            Text(if (open) "▲ پیامک‌های ارسال‌شده به طرف‌ها (${Digits.toPersian(g.recipients.size.toString())})"
                else "▼ ${Digits.toPersian(g.recipients.size.toString())} پیامک به طرف‌ها",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (open) {
                val tag = supersededTag(r)
                g.recipients.forEach { m ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${m.name}  ${m.phone}", style = MaterialTheme.typography.bodyMedium)
                        Text(statusLabel(m.status), color = statusColor(m.status), style = MaterialTheme.typography.labelMedium)
                    }
                    if (tag != null) Row(Modifier.fillMaxWidth()) {
                        Text(tag, color = requestColor(r), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
