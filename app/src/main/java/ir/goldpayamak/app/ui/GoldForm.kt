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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

/** فرم «طلایی»: پیامک به یک نفر؛ فروختم/خریدم، مبلغ و فی طلایی. با parentId اصلاحیه است. */
@Composable
fun GoldForm(vm: AppViewModel, parentId: Long?, onDone: (Long) -> Unit) {
    val ctx = LocalContext.current
    val slot = remember { Slot() }
    var side by remember { mutableStateOf(GoldSide.SELL) }
    var amountText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var permMsg by remember { mutableStateOf<String?>(null) }
    var appPick by remember { mutableStateOf(false) }
    var oldData by remember { mutableStateOf<GoldData?>(null) }
    var simChoices by remember { mutableStateOf<List<Sim>?>(null) }

    LaunchedEffect(parentId) {
        if (parentId != null) {
            val d = vm.goldOf(parentId)
            oldData = d
            slot.name = d.party.name; slot.phone = d.party.phone
            side = d.side
            amountText = d.amount.toString(); rateText = d.rate.toString()
        }
    }
    LaunchedEffect(vm.errorMsg) { if (vm.errorMsg != null) sending = false }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
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

    val amount = Price.parse(amountText)
    val rate = Price.parse(rateText)
    val data = GoldData(Party(slot.name.trim(), Phone.normalize(slot.phone) ?: slot.phone.trim()), side, amount ?: 0, rate ?: 0)
    val errors = Gold.validate(data)
    val plan = if (errors.isEmpty()) {
        if (parentId == null) Gold.original(vm.displayName, data)
        else oldData?.let { Gold.correction(vm.displayName, it, data, note) }
    } else null

    fun doSend() {
        sending = true
        if (parentId == null) vm.sendGold(data, onDone) else vm.sendGoldCorrection(parentId, data, note, onDone)
    }
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
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (ok) sendOrAskSim() else {
            val perms = buildList {
                add(Manifest.permission.SEND_SMS); add(Manifest.permission.READ_PHONE_STATE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permLauncher.launch(perms.toTypedArray())
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (parentId != null) {
            Text("اصلاحیه با پیشوند «اصلاحیه» برای گیرنده فرستاده می‌شود. اگر گیرنده را عوض کنید، گیرندهٔ قبلی پیام «کنسلی» می‌گیرد.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(note, { note = it }, label = { Text("توضیحات اصلاحیه (در پیامک هم می‌رود)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldSide.entries.forEach { s ->
                FilterChip(selected = side == s, onClick = { side = s }, label = { Text(s.word) })
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("گیرندهٔ پیامک", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(slot.name, { slot.name = it }, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(slot.phone, { slot.phone = it }, label = { Text("شمارهٔ موبایل") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }) { Text("مخاطبین گوشی") }
                    OutlinedButton(onClick = { appPick = true }) { Text("مخاطبین برنامه") }
                }
            }
        }
        OutlinedTextField(amountText, { amountText = it }, label = { Text("مبلغ (تومان)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            supportingText = { amount?.let { Text(Price.formatToman(it)) } })
        OutlinedTextField(rateText, { rateText = it }, label = { Text("فی طلایی (تومان)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            supportingText = { rate?.let { Text(Price.formatToman(it)) } })
        errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        permMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        plan?.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${p.party.name}  ${p.party.phone}" + if (p.kind == Kind.CANCEL) "  [کنسلی]" else if (p.kind == Kind.CORRECTION) "  [اصلاحیه]" else "",
                        style = MaterialTheme.typography.labelLarge)
                    Text(p.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(onClick = { confirm = true }, enabled = plan != null && !sending, modifier = Modifier.fillMaxWidth()) {
            Text(if (parentId == null) "ارسال و ثبت" else "ارسال اصلاحیه")
        }
    }

    if (confirm && plan != null) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("تأیید ارسال") },
        text = { Text("${plan.size} پیامک از سیم‌کارت این گوشی ارسال می‌شود. ادامه می‌دهید؟") },
        confirmButton = { TextButton(onClick = { confirm = false; requestSend() }) { Text("ارسال") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } },
    )

    simChoices?.let { sims ->
        AlertDialog(
            onDismissRequest = { simChoices = null },
            title = { Text("ارسال با کدام سیم‌کارت؟") },
            text = { Column { sims.forEach { s -> TextButton(onClick = { vm.prefs.subId = s.subId; simChoices = null; doSend() }, Modifier.fillMaxWidth()) { Text(s.label, Modifier.fillMaxWidth()) } } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { simChoices = null }) { Text("انصراف") } },
        )
    }

    if (appPick) {
        val contacts by vm.contacts.collectAsState()
        AppContactPickerDialog(contacts, onPick = { c -> slot.name = c.name; slot.phone = c.phone; appPick = false }, onDismiss = { appPick = false })
    }
}
