package ir.goldpayamak.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.core.Digits

data class Sim(val subId: Int, val label: String)

/** سیم‌کارت‌های فعال؛ نیاز به مجوز READ_PHONE_STATE دارد (بدون مجوز لیست خالی). */
fun listSims(ctx: android.content.Context): List<Sim> {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
    return try {
        ctx.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().map {
            Sim(it.subscriptionId, "سیم ${Digits.toPersian((it.simSlotIndex + 1).toString())}: ${it.displayName} ${it.number.orEmpty()}".trim())
        }
    } catch (e: SecurityException) { emptyList() }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var delay by remember { mutableStateOf(vm.prefs.delaySeconds.toString()) }
    var subId by remember { mutableIntStateOf(vm.prefs.subId) }
    var sims by remember { mutableStateOf<List<Sim>>(emptyList()) }
    var needPerm by remember { mutableStateOf(false) }

    fun loadSims() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needPerm = true; return
        }
        needPerm = false
        sims = listSims(ctx)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { loadSims() }
    LaunchedEffect(Unit) { loadSims() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("سیم‌کارت ارسال", style = MaterialTheme.typography.titleSmall)
        if (needPerm) {
            Text("برای انتخاب سیم‌کارت، مجوز خواندن وضعیت تلفن لازم است. اگر انتخاب نکنید از سیم‌کارت پیش‌فرض پیامک گوشی استفاده می‌شود.")
            OutlinedButton(onClick = { perm.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("دادن مجوز") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = subId == -1, onClick = { subId = -1; vm.prefs.subId = -1 })
            Text("پیش‌فرض گوشی")
        }
        sims.forEach { s ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = subId == s.subId, onClick = { subId = s.subId; vm.prefs.subId = s.subId })
                Text(s.label)
            }
        }
        HorizontalDivider()
        OutlinedTextField(delay, { delay = it; Digits.toLatin(it).toIntOrNull()?.let { v -> vm.prefs.delaySeconds = v } },
            label = { Text("وقفه بین پیامک‌ها (ثانیه)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("برای جلوگیری از مسدود شدن توسط اپراتور، وقفه را زیاد کم نکنید.") })
        HorizontalDivider()
        Text("توجه: گوشی باید روشن و برنامه باز باشد تا ارسال کامل شود. پیامک فارسی در هر بخش ۷۰ کاراکتر است و پیام طولانی چندبخشی حساب می‌شود.",
            style = MaterialTheme.typography.bodySmall)
    }
}
