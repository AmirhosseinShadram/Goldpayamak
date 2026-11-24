package ir.goldpayamak.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import ir.goldpayamak.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ir.goldpayamak.app.data.RequestEntity
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.data.MessageEntity
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import ir.goldpayamak.core.Digits
import ir.goldpayamak.core.Planned
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
fun SenderNameField(
    name: String,
    onNameChange: (String) -> Unit,
    names: List<String>,
    onRemove: (String) -> Unit,
) {
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
                        Row(Modifier.width(240.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            DropdownMenuItem(
                                text = { Text(previous) },
                                onClick = {
                                    onNameChange(previous)
                                    expanded = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onRemove(previous)
                                if (names.size == 1) expanded = false
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormattedAmountHint(amount: String) {
    Text(
        text = amount,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}


@Composable
fun ContactSourcePicker(onPhone: () -> Unit, onApp: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPhone, modifier = Modifier.weight(1f)) { Text("مخاطبین گوشی") }
        OutlinedButton(onClick = onApp, modifier = Modifier.weight(1f)) { Text("مخاطبین برنامه") }
    }
}

@Composable
fun StatusTag(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), contentColor = color, shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagePreviewDialog(plan: List<Planned>, onDismiss: () -> Unit, onSend: () -> Unit) {
    val count = Digits.toPersian(plan.count { !it.isSelf }.toString())
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("پیش‌نمایش پیامک") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                        }
                    },
                    actions = { SupportCallButton() },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Button(onClick = onSend, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 48.dp)) {
                        Text("ارسال و ثبت")
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("$count پیامک از سیم‌کارت این گوشی ارسال می‌شود.", style = MaterialTheme.typography.bodySmall) }
                items(plan) { message ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (message.isSelf) "شما · فقط ثبت می‌شود" else message.party.phone, style = MaterialTheme.typography.labelMedium)
                            Text(message.text, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}


private const val SUPPORT_PHONE = "09123399729"

@Composable
fun SupportCallButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var dialerUnavailable by remember { mutableStateOf(false) }

    IconButton(
        modifier = modifier,
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", SUPPORT_PHONE, null)))
            } catch (_: ActivityNotFoundException) {
                dialerUnavailable = true
            }
        },
    ) {
        Icon(painterResource(R.drawable.ic_support_headset), contentDescription = "تماس با پشتیبانی")
    }
    if (dialerUnavailable) {
        AlertDialog(
            onDismissRequest = { dialerUnavailable = false },
            text = { Text("برای تماس با پشتیبانی شمارهٔ $SUPPORT_PHONE را بگیرید.") },
            confirmButton = { TextButton(onClick = { dialerUnavailable = false }) { Text("باشه") } },
        )
    }
}


@Composable
fun SmsPermissionHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var settingsUnavailable by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مجوز پیامک لازم است") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("در تنظیمات گلدپیامک به «مجوزها ← پیامک» بروید و «اجازه دادن» را انتخاب کنید. سپس به برنامه برگردید و ارسال را دوباره تأیید کنید.")
                if (settingsUnavailable) {
                    Text("از تنظیمات گوشی، «برنامه‌ها ← گلدپیامک» را باز کنید.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                    onDismiss()
                } catch (_: ActivityNotFoundException) {
                    settingsUnavailable = true
                }
            }) { Text("باز کردن تنظیمات برنامه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("فعلاً نه") } },
    )
}
