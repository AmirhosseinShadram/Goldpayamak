package ir.goldpayamak.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ir.goldpayamak.app.data.RequestEntity
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.data.MessageEntity
import ir.goldpayamak.app.data.Status
import ir.goldpayamak.app.kindLabel
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Jalali
import ir.goldpayamak.core.Price

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
                "${kindLabel(m.kind)} • ${Jalali.fromMillis(m.createdAt).format()} ${Jalali.timeOf(m.createdAt)} • قیمت ${Price.formatToman(m.price)}",
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
