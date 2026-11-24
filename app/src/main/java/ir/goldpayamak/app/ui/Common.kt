package ir.goldpayamak.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ir.goldpayamak.app.data.RequestEntity
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.data.MessageEntity
import androidx.compose.foundation.clickable
import ir.goldpayamak.app.data.Status
import ir.goldpayamak.app.kindLabel
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Jalali

/** رنگ اصلاحیه: زرد/کهربایی (روی زمینهٔ روشن خوانا) */
val AmberColor = Color(0xFFB26A00)

/** در صفحه‌های جزئیات و گزارش ایموجی نمایش داده نمی‌شود (در خود پیامک می‌ماند). */
fun plain(t: String): String = t.replace("\u274C", "").replace("\u2757", "").lines().joinToString("\n") { it.trim() }

/** رنگ وضعیت درخواست: کنسل = قرمز، اصلاح = زرد، بقیه = خنثی */
@Composable
fun requestColor(r: RequestEntity) = when {
    r.status == Status.REQ_CANCELLED || r.kind == "CANCEL" -> MaterialTheme.colorScheme.error
    r.status == Status.REQ_SUPERSEDED || r.kind == "CORRECTION" -> AmberColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun statusColor(s: String) = when (s) {
    Status.DELIVERED -> MaterialTheme.colorScheme.primary
    Status.FAILED, Status.REQ_CANCELLED -> MaterialTheme.colorScheme.error
    Status.REQ_SUPERSEDED -> AmberColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MessageCard(m: MessageEntity, showText: Boolean = true) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${m.name}  ${m.phone}".trim(), style = MaterialTheme.typography.titleSmall)
                Text(statusLabel(m.status), color = statusColor(m.status), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${kindLabel(m.kind)} • ${Jalali.fromMillis(m.createdAt).format()} ${Jalali.timeOf(m.createdAt)} • ${m.transferAmount.format()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showText) Text(plain(m.text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** متن اصلی یک درخواست: پیام طرف ۲ (حواله) یا پیام گیرنده (طلایی) */
fun mainMessage(r: RequestEntity, msgs: List<MessageEntity>?): MessageEntity? =
    msgs?.firstOrNull { it.status == Status.SELF } ?: msgs?.firstOrNull { it.kind == r.kind } ?: msgs?.firstOrNull()

/** وضعیت کل زنجیره بر اساس آخرین اقدام: کنسلی = کنسل شده، اصلاحیه = اصلاح شده، بدون اقدام = فعال */
fun chainState(actions: List<RequestEntity>): String = when (actions.lastOrNull()?.kind) {
    "CANCEL" -> "کنسل شده"
    "CORRECTION" -> "اصلاح شده"
    else -> "فعال"
}

@Composable
fun chainColor(actions: List<RequestEntity>) = when (actions.lastOrNull()?.kind) {
    "CANCEL" -> MaterialTheme.colorScheme.error
    "CORRECTION" -> AmberColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** کارهای انجام‌شده روی یک درخواست (اصلاحیه/کنسلی)؛ با زدن روی هر کدام، پیامش نمایش داده می‌شود. */
@Composable
fun ActionRows(actions: List<RequestEntity>, onClick: (RequestEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { a ->
            val cancel = a.kind == "CANCEL"
            Text(
                (if (cancel) "کنسلی" else "اصلاحیه") + "  •  ${Jalali.fromMillis(a.createdAt).format()} ${Jalali.timeOf(a.createdAt)}",
                color = if (cancel) MaterialTheme.colorScheme.error else AmberColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable { onClick(a) }.padding(vertical = 6.dp),
            )
        }
    }
}

/** پنجرهٔ یک اصلاحیه/کنسلی: متن پیام، توضیحات و وضعیت ارسال همان پیامک برای هر طرف. */
@Composable
fun ActionDialog(a: RequestEntity, msgs: List<MessageEntity>, onDismiss: () -> Unit) {
    val cancel = a.kind == "CANCEL"
    val msg = mainMessage(a, msgs)
    val sent = msgs.filter { it.status != Status.SELF }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cancel) "کنسلی" else "اصلاحیه", color = if (cancel) MaterialTheme.colorScheme.error else AmberColor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${Jalali.fromMillis(a.createdAt).format()}  ${Jalali.timeOf(a.createdAt)}", style = MaterialTheme.typography.labelMedium)
                Text(plain(msg?.text ?: "—"), style = MaterialTheme.typography.bodyMedium)
                if (a.note.isNotBlank() && msg?.text?.contains(a.note) != true) Text("توضیحات: ${a.note}", style = MaterialTheme.typography.bodyMedium)
                if (sent.isNotEmpty()) {
                    HorizontalDivider()
                    Text("وضعیت ارسال", style = MaterialTheme.typography.titleSmall)
                    sent.forEach { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.name}  ${m.phone}", style = MaterialTheme.typography.bodyMedium)
                                if (a.type == "RING") Text(m.transferAmount.format(), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(statusLabel(m.status), color = statusColor(m.status), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
fun SenderNameField(name: String, onNameChange: (String) -> Unit, names: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("نام شما در پیامک") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (names.isNotEmpty()) {
            Box {
                TextButton(onClick = { expanded = true }) { Text("انتخاب از نام‌های قبلی") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    names.forEach { previous ->
                        DropdownMenuItem(text = { Text(previous) }, onClick = {
                            onNameChange(previous)
                            expanded = false
                        })
                    }
                }
            }
        }
    }
}
