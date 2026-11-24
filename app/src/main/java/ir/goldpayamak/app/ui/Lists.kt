@file:OptIn(ExperimentalMaterial3Api::class)

package ir.goldpayamak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.app.data.ContactEntity
import ir.goldpayamak.app.data.Status
import ir.goldpayamak.app.statusLabel
import ir.goldpayamak.core.Jalali
import ir.goldpayamak.core.Phone
import ir.goldpayamak.core.TextNorm

@Composable
fun HistoryScreen(
    vm: AppViewModel,
    isGold: Boolean,
    onOpen: (Long) -> Unit,
    onStart: (() -> Unit)? = null,
) {
    val roots by vm.roots.collectAsState()
    val actions by vm.actions.collectAsState()
    val messages by (if (isGold) vm.goldMessages else vm.selfMessages).collectAsState()
    val messagesByRequest = remember(messages) { messages.groupBy { it.requestId } }
    val actionsByRoot = remember(actions) { actions.groupBy { it.rootId } }
    val type = if (isGold) "GOLD" else "RING"
    var search by rememberSaveable(isGold) { mutableStateOf("") }
    val query = TextNorm.history(search)
    val entries by remember(type) { vm.dao.historySearchEntries(type) }.collectAsState(initial = emptyList())
    val matches = remember(entries, query) {
        if (query.isBlank()) emptySet() else entries.asSequence()
            .filter { entry ->
                val amount = entry.goldGrams ?: entry.price.toString()
                listOfNotNull(entry.name, entry.phone, entry.text, amount, entry.rate?.toString())
                    .any { TextNorm.history(it).contains(query) }
            }
            .map { it.rootId }.toSet()
    }
    val history = remember(roots, type, query, matches) {
        roots.filter { it.type == type && (query.isBlank() || it.id in matches) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onStart != null) item {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(if (isGold) "ثبت طلایی جدید" else "ثبت حواله جدید") }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("جستجو با نام، شماره یا مبلغ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isGold && history.isNotEmpty()) item {
            Text("آخرین طلایی‌ها", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (history.isEmpty()) item {
            Text(
                when {
                    query.isNotBlank() -> "نتیجه‌ای پیدا نشد."
                    isGold -> "هنوز طلایی‌ای ثبت نشده است."
                    else -> "هنوز حواله‌ای ثبت نشده است."
                },
                Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(history, key = { it.id }) { request ->
            val changes = actionsByRoot[request.id].orEmpty()
            OutlinedCard(onClick = { onOpen(request.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("${Jalali.fromMillis(request.createdAt).format()}  ${Jalali.timeOf(request.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        StatusTag(chainState(changes), chainColor(changes))
                    }
                    Text(
                        plain(mainMessage(request, messagesByRequest[request.id])?.text ?: "—"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onCorrect: (Long, Boolean) -> Unit, onOpen: (Long) -> Unit) {
    val anyReq by remember(id) { vm.dao.request(id) }.collectAsState(initial = null)
    val rootId = anyReq?.rootId
    if (rootId == null) { EmptyHint("..."); return }
    val chain by remember(rootId) { vm.dao.chainOf(rootId) }.collectAsState(initial = emptyList())
    val allMsgs by remember(rootId) { vm.dao.messagesOfRoot(rootId) }.collectAsState(initial = emptyList())
    var confirmCancel by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<ir.goldpayamak.app.data.RequestEntity?>(null) }
    val root = chain.firstOrNull { it.id == rootId }
    if (root == null) { EmptyHint("..."); return }
    val actions = chain.filter { it.id != rootId }
    val latest = chain.lastOrNull() // آخرین نسخه؛ اصلاحیه و کنسلی هر چند بار روی آن اعمال می‌شود
    val isGold = root.type == "GOLD"
    val byReq = remember(allMsgs) { allMsgs.groupBy { it.requestId } }
    val rootText = mainMessage(root, byReq[root.id])?.text
    // وضعیت ارسال فقط برای پیام اول؛ وضعیت اصلاحیه‌ها و کنسلی‌ها داخل پاپ‌آپ خودشان است
    val recipients = byReq[root.id].orEmpty().filter { it.status != Status.SELF }
    val failedReqs = allMsgs.filter { it.status == Status.FAILED }.map { it.requestId }.distinct()
    val busy = allMsgs.any { it.status == Status.PENDING || it.status == Status.SENDING }
    val red = MaterialTheme.colorScheme.error

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${Jalali.fromMillis(root.createdAt).format()}  ${Jalali.timeOf(root.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        Text(chainState(actions), color = chainColor(actions), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(plain(rootText ?: "—"), style = MaterialTheme.typography.titleMedium)
                    if (latest != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onCorrect(latest.id, isGold) }, enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberColor, contentColor = androidx.compose.ui.graphics.Color.White)) { Text("اصلاحیه") }
                            Button(onClick = { confirmCancel = true }, enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = androidx.compose.ui.graphics.Color.White)) { Text("کنسلی") }
                        }
                    }
                }
            }
        }
        if (failedReqs.isNotEmpty()) item { Button(onClick = { failedReqs.forEach { vm.retry(it) } }) { Text("ارسال مجدد ناموفق‌ها") } }
        item { Text(if (isGold) "وضعیت ارسال" else "وضعیت ارسال به طرف‌ها", style = MaterialTheme.typography.titleSmall) }
        items(recipients, key = { it.id }) { RecipientRow(it) }
        if (actions.isNotEmpty()) {
            item { Text("کارهای انجام‌شده", style = MaterialTheme.typography.titleSmall) }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 14.dp)) { ActionRows(actions) { shown = it } } } }
        }
    }
    shown?.let { a -> ActionDialog(a, byReq[a.id].orEmpty()) { shown = null } }
    if (confirmCancel && latest != null) {
        var cancelNote by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("کنسلی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("برای همهٔ طرف‌ها پیامک «کنسلی» ارسال می‌شود. ادامه می‌دهید؟")
                    if (!isGold) Text(latest.transferAmount.format(), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(cancelNote, { cancelNote = it }, label = { Text("توضیحات کنسلی (در پیامک هم می‌رود)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel(latest.id, cancelNote) { onOpen(it) } }) { Text("کنسلی و ارسال") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("انصراف") } },
        )
    }
}

/** وضعیت ارسال یک طرف: نام، شماره و وضعیت (متن پیامک نمایش داده نمی‌شود). */
@Composable
private fun RecipientRow(m: ir.goldpayamak.app.data.MessageEntity) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${m.name}  ${m.phone}", style = MaterialTheme.typography.titleSmall)
            StatusTag(statusLabel(m.status), statusColor(m.status))
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
