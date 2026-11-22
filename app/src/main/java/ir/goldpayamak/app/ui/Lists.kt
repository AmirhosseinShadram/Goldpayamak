@file:OptIn(ExperimentalMaterial3Api::class)

package ir.goldpayamak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.app.data.ContactEntity
import ir.goldpayamak.app.data.Status
import ir.goldpayamak.app.data.requestStateLabel
import ir.goldpayamak.app.data.supersededTag
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Jalali
import ir.goldpayamak.core.Phone
import ir.goldpayamak.core.Price

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val list by vm.requests.collectAsState()
    val selfs by vm.selfMessages.collectAsState()
    val golds by vm.goldMessages.collectAsState()
    val selfByReq = remember(selfs, golds) { (selfs + golds).associateBy { it.requestId } }
    if (list.isEmpty()) { EmptyHint("هنوز درخواستی ثبت نشده است."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(r.id) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Jalali.fromMillis(r.createdAt).format()}  ${Jalali.timeOf(r.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        Text((if (r.type == "GOLD") "طلایی • " else "حواله • ") + requestStateLabel(r), color = statusColor(r.status), style = MaterialTheme.typography.labelMedium)
                    }
                    // متن پیام طرف ۲ (خود کاربر)؛ با زدن روی آن می‌شود اصلاح یا کنسل کرد
                    Text(selfByReq[r.id]?.text ?: "—", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onCorrect: (Long, Boolean) -> Unit, onOpen: (Long) -> Unit) {
    val req by remember(id) { vm.dao.request(id) }.collectAsState(initial = null)
    val msgs by remember(id) { vm.dao.messagesOf(id) }.collectAsState(initial = emptyList())
    val children by remember(id) { vm.dao.children(id) }.collectAsState(initial = emptyList())
    var confirmCancel by remember { mutableStateOf(false) }
    val r = req
    if (r == null) { EmptyHint("..."); return }
    val isGold = r.type == "GOLD"
    val self = msgs.firstOrNull { it.status == Status.SELF } ?: if (isGold) msgs.firstOrNull() else null
    val recipients = msgs.filter { it.status != Status.SELF }
    val failed = recipients.any { it.status == Status.FAILED }
    val busy = recipients.any { it.status == Status.PENDING || it.status == Status.SENDING }
    val tag = supersededTag(r)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Jalali.fromMillis(r.createdAt).format()}  ${Jalali.timeOf(r.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        Text(requestStateLabel(r), color = statusColor(r.status), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(self?.text ?: "—", style = MaterialTheme.typography.titleSmall)
                    if (r.note.isNotBlank()) Text("توضیحات: ${r.note}", style = MaterialTheme.typography.bodyMedium)
                    if (r.status == Status.REQ_ACTIVE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onCorrect(r.id, isGold) }, enabled = !busy) { Text("اصلاحیه ❗❗") }
                            OutlinedButton(onClick = { confirmCancel = true }, enabled = !busy) { Text("کنسلی ❌❌") }
                        }
                    }
                    if (r.parentId != null) TextButton(onClick = { onOpen(r.parentId) }) { Text("مشاهدهٔ نسخهٔ قبلی") }
                }
            }
        }
        if (failed) item { Button(onClick = { vm.retry(id) }) { Text("ارسال مجدد ناموفق‌ها") } }
        item { Text(if (isGold) "وضعیت پیامک ارسال‌شده" else "پیامک‌های ارسال‌شده به طرف‌ها", style = MaterialTheme.typography.titleSmall) }
        items(recipients, key = { it.id }) { RecipientRow(it, tag) }
        if (children.isNotEmpty()) {
            item { Text("اصلاحیه / کنسلی این درخواست", style = MaterialTheme.typography.titleSmall) }
            items(children, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (c.kind == "CANCEL") "کنسلی ❌❌" else "اصلاحیه ❗❗", style = MaterialTheme.typography.titleSmall)
                        Text("${Jalali.fromMillis(c.createdAt).format()}  ${Jalali.timeOf(c.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (confirmCancel) {
        var cancelNote by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("کنسلی ❌❌") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("برای همهٔ طرف‌ها پیامک «کنسلی» ارسال می‌شود. ادامه می‌دهید؟")
                    OutlinedTextField(cancelNote, { cancelNote = it }, label = { Text("توضیحات کنسلی (در پیامک هم می‌رود)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel(id, cancelNote) { onOpen(it) } }) { Text("کنسلی و ارسال") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("انصراف") } },
        )
    }
}

/** پیامک یک طرف: نام، شماره و وضعیت؛ با زدن، متن پیامک باز می‌شود. */
@Composable
private fun RecipientRow(m: ir.goldpayamak.app.data.MessageEntity, tag: String?) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${m.name}  ${m.phone}", style = MaterialTheme.typography.titleSmall)
                Text(statusLabel(m.status) + (tag?.let { " • $it" } ?: ""), color = statusColor(m.status), style = MaterialTheme.typography.labelMedium)
            }
            if (open) Text(m.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ContactHistoryScreen(vm: AppViewModel, phone: String) {
    val msgs by remember(phone) { vm.dao.messagesOfPhone(phone) }.collectAsState(initial = emptyList())
    if (msgs.isEmpty()) { EmptyHint("برای این مخاطب پیامی ثبت نشده است."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(msgs) { MessageCard(it) }
    }
}

@Composable
fun ContactsScreen(vm: AppViewModel, onHistory: (String, String) -> Unit) {
    val all by vm.contacts.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ContactEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val list = all.filter { query.isBlank() || it.name.contains(query.trim()) || it.phone.contains(ir.goldpayamak.core.Digits.toLatin(query.trim())) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("جستجو") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) { Text("افزودن") }
        }
        if (list.isEmpty()) EmptyHint("مخاطبی وجود ندارد.")
        else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { c ->
                Card(Modifier.fillMaxWidth().clickable { onHistory(c.name, c.phone) }) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Text(c.phone, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = { editing = c }) { Text("ویرایش") }
                            TextButton(onClick = { vm.deleteContact(c) }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val c = editing
        var name by remember(c, adding) { mutableStateOf(c?.name ?: "") }
        var phone by remember(c, adding) { mutableStateOf(c?.phone ?: "") }
        val normalized = Phone.normalize(phone)
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (c == null) "مخاطب جدید" else "ویرایش مخاطب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("شمارهٔ موبایل") }, singleLine = true,
                        isError = phone.isNotBlank() && normalized == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && normalized != null, onClick = {
                    vm.saveContact(c, name, normalized!!); adding = false; editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("انصراف") } },
        )
    }
}
