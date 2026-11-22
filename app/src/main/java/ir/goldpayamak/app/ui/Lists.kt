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
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Jalali
import ir.goldpayamak.core.Phone
import ir.goldpayamak.core.Price

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val list by vm.requests.collectAsState()
    if (list.isEmpty()) { EmptyHint("هنوز درخواستی ثبت نشده است."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list) { r ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(r.id) }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Jalali.fromMillis(r.createdAt).format()}  ${Jalali.timeOf(r.createdAt)}", style = MaterialTheme.typography.titleSmall)
                        Text(if (r.kind == "CORRECTION") "اصلاحیه • ${statusLabel(r.status)}" else statusLabel(r.status),
                            color = statusColor(r.status), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("قیمت: ${Price.format(r.price)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onCorrect: (Long) -> Unit, onOpen: (Long) -> Unit) {
    val req by remember(id) { vm.dao.request(id) }.collectAsState(initial = null)
    val rootId = req?.rootId
    val msgs by remember(id, rootId) { if (rootId != null) vm.dao.messagesOfRoot(rootId) else vm.dao.messagesOf(id) }
        .collectAsState(initial = emptyList())
    var confirmCancel by remember { mutableStateOf(false) }
    val r = req
    if (r == null) { EmptyHint("..."); return }
    val own = msgs.filter { it.requestId == id }
    val failed = own.any { it.status == Status.FAILED }
    val busy = own.any { it.status == Status.PENDING || it.status == Status.SENDING }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("وضعیت: ${statusLabel(r.status)} • قیمت ${Price.format(r.price)}", style = MaterialTheme.typography.titleMedium)
            Text("${Jalali.fromMillis(r.createdAt).format()} ${Jalali.timeOf(r.createdAt)}", style = MaterialTheme.typography.bodySmall)
        }
        if (r.status == Status.REQ_ACTIVE) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCorrect(r.id) }, enabled = !busy) { Text("اصلاحیه") }
                OutlinedButton(onClick = { confirmCancel = true }, enabled = !busy) { Text("لغو") }
            }
        }
        if (failed) item { Button(onClick = { vm.retry(id) }) { Text("ارسال مجدد ناموفق‌ها") } }
        item { Text("پیامک‌های همین ثبت", style = MaterialTheme.typography.titleSmall) }
        items(own) { MessageCard(it) }
        val others = msgs.filter { it.requestId != id }
        if (others.isNotEmpty()) {
            item { Text("سایر نسخه‌های این درخواست (اصلی/اصلاحیه/لغو)", style = MaterialTheme.typography.titleSmall) }
            items(others) { MessageCard(it, showText = false) }
        }
    }
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("لغو درخواست") },
        text = { Text("برای همهٔ طرف‌ها پیامک «لغو» ارسال می‌شود. ادامه می‌دهید؟") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel(id) { onOpen(it) } }) { Text("لغو و ارسال") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("انصراف") } },
    )
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
