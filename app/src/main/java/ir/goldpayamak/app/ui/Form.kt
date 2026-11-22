package ir.goldpayamak.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.core.*

class Slot(name: String = "", phone: String = "") {
    var name by mutableStateOf(name)
    var phone by mutableStateOf(phone)
}

/** شمارهٔ نمایشی طرف: اسلات‌ها [طرف۱، طرف۳، طرف۴، ...]؛ طرف ۲ همیشه خود کاربر است. */
private fun label(j: Int) = "طرف ${Digits.toPersian((if (j == 0) 1 else j + 2).toString())}"

@Composable
fun FormScreen(vm: AppViewModel, parentId: Long?, onDone: (Long) -> Unit) {
    val ctx = LocalContext.current
    val slots = remember { mutableStateListOf(Slot(), Slot()) }
    var priceText by remember { mutableStateOf("") }
    var oldRing by remember { mutableStateOf<List<Party>?>(null) }
    var oldPrice by remember { mutableLongStateOf(0L) }
    var pickTarget by remember { mutableStateOf<Slot?>(null) }
    var appPickTarget by remember { mutableStateOf<Slot?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var permMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(parentId) {
        if (parentId != null) {
            val ring = vm.ringOf(parentId)
            val req = vm.requestOnce(parentId)
            oldRing = ring
            oldPrice = req?.price ?: 0
            slots.clear()
            ring.forEachIndexed { i, p -> if (i != Ring.USER_INDEX) slots.add(Slot(p.name, p.phone)) }
            priceText = req?.price?.toString() ?: ""
        }
    }

    LaunchedEffect(vm.errorMsg) { if (vm.errorMsg != null) sending = false }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        val slot = pickTarget
        if (r.resultCode == Activity.RESULT_OK && uri != null && slot != null) {
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    slot.name = c.getString(0) ?: ""
                    slot.phone = Phone.normalize(c.getString(1) ?: "") ?: (c.getString(1) ?: "")
                }
            }
        }
    }

    val ring: List<Party> = remember(slots.toList().map { it.name to it.phone }, vm.displayName) {
        val others = slots.map { Party(it.name.trim(), Phone.normalize(it.phone) ?: it.phone.trim()) }
        if (others.size < 2) emptyList() else buildList {
            add(others[0]); add(Party(vm.displayName, "")); addAll(others.drop(1))
        }
    }
    val price = Price.parse(priceText)
    val errors = Planner.validate(ring) + (if (price == null) listOf("قیمت را به عدد (بزرگ‌تر از صفر) وارد کنید") else emptyList())
    val plan = if (errors.isEmpty() && price != null) {
        if (parentId == null) Planner.original(vm.displayName, ring, price)
        else oldRing?.let { Planner.correction(vm.displayName, it, oldPrice, ring, price) }
    } else null

    var simChoices by remember { mutableStateOf<List<Sim>?>(null) }

    fun doSend() {
        val p = price ?: return
        sending = true
        if (parentId == null) vm.sendNew(ring, p, onDone) else vm.sendCorrection(parentId, ring, p, onDone)
    }

    // گوشی دو‌سیم‌کارته بدون سیم پیش‌فرض پیامک: قبل از ارسال از کاربر می‌پرسیم (انتخاب در تنظیمات ذخیره می‌شود)
    fun sendOrAskSim() {
        val sims = listSims(ctx)
        val defaultSms = if (Build.VERSION.SDK_INT >= 24) android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId() else -1
        val noDefault = defaultSms == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
        if (vm.prefs.subId == -1 && sims.size >= 2 && noDefault) simChoices = sims else doSend()
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.SEND_SMS] == true) sendOrAskSim()
        else { permMsg = "برای ارسال پیامک مجوز «پیامک» لازم است."; sending = false }
    }

    fun requestSend() {
        val smsOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (smsOk) sendOrAskSim() else {
            val perms = buildList {
                add(Manifest.permission.SEND_SMS); add(Manifest.permission.READ_PHONE_STATE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permLauncher.launch(perms.toTypedArray())
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (parentId != null) item {
            Text("اصلاحیه برای همهٔ طرف‌های جدید فرستاده می‌شود؛ کسی که حذف کنید پیام «لغو» می‌گیرد و کسی که اضافه کنید پیام اصلی.",
                style = MaterialTheme.typography.bodySmall)
        }
        // ترتیب نمایش: طرف ۱، طرف ۲ (شما)، طرف ۳، ...
        items(slots.size + 1) { row ->
            if (row == 1) {
                Card(Modifier.fillMaxWidth()) {
                    Text("طرف ۲: ${vm.displayName} (شما)", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                }
            } else {
                val j = if (row == 0) 0 else row - 1
                val slot = slots[j]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(label(j), style = MaterialTheme.typography.titleSmall)
                            if (j >= 2) TextButton(onClick = { slots.removeAt(j) }) { Text("حذف") }
                        }
                        OutlinedTextField(slot.name, { slot.name = it }, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(slot.phone, { slot.phone = it }, label = { Text("شمارهٔ موبایل") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                pickTarget = slot
                                pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                            }) { Text("مخاطبین گوشی") }
                            OutlinedButton(onClick = { appPickTarget = slot }) { Text("مخاطبین برنامه") }
                        }
                    }
                }
            }
        }
        item {
            if (slots.size + 1 < Ring.MAX_PARTIES) OutlinedButton(onClick = { slots.add(Slot()) }, Modifier.fillMaxWidth()) { Text("افزودن طرف") }
        }
        item {
            OutlinedTextField(priceText, { priceText = it }, label = { Text("قیمت (یکسان برای همهٔ طرف‌ها)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                supportingText = { price?.let { Text(Price.format(it)) } })
        }
        if (errors.isNotEmpty()) item { errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        permMsg?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }
        plan?.let { pl ->
            item { Text("پیش‌نمایش پیامک‌ها", style = MaterialTheme.typography.titleMedium) }
            items(pl) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            (if (p.isSelf) "${p.party.name} (شما) — فقط ثبت می‌شود" else "${p.party.name}  ${p.party.phone}") +
                                if (p.kind == Kind.CANCEL) "  [لغو]" else if (p.kind == Kind.CORRECTION) "  [اصلاحیه]" else "",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(p.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Button(onClick = { confirm = true }, enabled = plan != null && !sending, modifier = Modifier.fillMaxWidth()) {
                Text(if (parentId == null) "ارسال و ثبت" else "ارسال اصلاحیه")
            }
        }
    }

    if (confirm && plan != null) {
        val n = plan.count { !it.isSelf }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("تأیید ارسال") },
            text = { Text("$n پیامک از سیم‌کارت این گوشی ارسال می‌شود (هزینه از اعتبار سیم‌کارت شما کم می‌شود). ادامه می‌دهید؟") },
            confirmButton = { TextButton(onClick = { confirm = false; requestSend() }) { Text("ارسال") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } },
        )
    }

    simChoices?.let { sims ->
        AlertDialog(
            onDismissRequest = { simChoices = null },
            title = { Text("ارسال با کدام سیم‌کارت؟") },
            text = {
                Column {
                    sims.forEach { s ->
                        TextButton(onClick = { vm.prefs.subId = s.subId; simChoices = null; doSend() }, Modifier.fillMaxWidth()) {
                            Text(s.label, Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { simChoices = null }) { Text("انصراف") } },
        )
    }

    appPickTarget?.let { target ->
        val contacts by vm.contacts.collectAsState()
        AlertDialog(
            onDismissRequest = { appPickTarget = null },
            title = { Text("انتخاب از مخاطبین برنامه") },
            text = {
                if (contacts.isEmpty()) Text("هنوز مخاطبی ذخیره نشده است.")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(contacts) { c ->
                        TextButton(onClick = { target.name = c.name; target.phone = c.phone; appPickTarget = null }, Modifier.fillMaxWidth()) {
                            Text("${c.name}  ${c.phone}", Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { appPickTarget = null }) { Text("بستن") } },
        )
    }
}
