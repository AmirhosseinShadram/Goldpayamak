package ir.goldpayamak.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.app.BuildConfig
import ir.goldpayamak.app.data.ActivationResult
import ir.goldpayamak.core.Digits

const val CODE_LENGTH = 12

/** فقط رقم (فارسی/عربی هم پذیرفته و به لاتین تبدیل می‌شود)، حداکثر ۱۲ رقم */
private fun cleanCode(s: String) = Digits.toLatin(s).filter { it in '0'..'9' }.take(CODE_LENGTH)

/** فعال‌سازی یک‌باره با کد ۱۲ رقمی یک‌بارمصرف. بعد از موفقیت دیگر نمایش داده نمی‌شود. */
@Composable
fun AuthScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp)) {
                SupportCallButton(Modifier.align(Alignment.CenterEnd))
            }
        },
    ) { insets ->
        ActivationForm(vm, Modifier.padding(insets))
    }
}

@Composable
private fun ActivationForm(vm: AppViewModel, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf(vm.displayName) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            "برای دریافت کد فعال‌سازی یا راهنمایی، روی آیکن پشتیبانی بزنید.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (BuildConfig.OFFLINE_LICENSE) {
            OfflineActivationForm(vm)
            return@Column
        }
        if (BuildConfig.DEMO) {
            Text("نسخهٔ دمو", style = MaterialTheme.typography.headlineMedium)
            Text("این نسخه فقط برای آزمایش است و کد فعال‌سازی نمی‌خواهد. پیامک‌ها واقعاً ارسال می‌شوند؛ برای تست از شماره‌های خودتان استفاده کنید.")
            OutlinedTextField(name, { name = it }, label = { Text("نام شما (اول هر پیامک می‌آید)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank(), onClick = { vm.activateDemo(name) }) { Text("ورود به دمو") }
            return@Column
        }
        Text("فعال‌سازی برنامه", style = MaterialTheme.typography.headlineMedium)
        Text("کد ۱۲ رقمی که به شما داده شده را وارد کنید. کد فقط یک بار قابل استفاده است و بعد از فعال‌سازی دیگر لازم نیست وارد شود (تا وقتی برنامه را حذف نکنید). برای فعال‌سازی به اینترنت نیاز است.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(name, { name = it }, label = { Text("نام شما (اول هر پیامک می‌آید)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = cleanCode(it) }, label = { Text("کد فعال‌سازی ۱۲ رقمی") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("${Digits.toPersian(code.length.toString())} از ${Digits.toPersian(CODE_LENGTH.toString())}") },
            modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = code.length == CODE_LENGTH && !vm.activating,
            onClick = {
                if (name.isBlank()) { error = "نام را وارد کنید"; return@Button }
                error = null
                vm.activate(name, code) { r ->
                    error = when (r) {
                        ActivationResult.Ok -> null
                        ActivationResult.Invalid -> "کد نامعتبر است"
                        ActivationResult.Used -> "این کد قبلاً استفاده شده است"
                        ActivationResult.TooMany -> "تلاش‌های زیاد؛ چند دقیقه بعد دوباره امتحان کنید"
                        ActivationResult.NotConfigured -> "آدرس سرور فعال‌سازی در این نسخه تنظیم نشده است"
                        is ActivationResult.NetworkError -> "اتصال به سرور برقرار نشد (${r.detail}). اینترنت را بررسی کنید."
                    }
                }
            },
        ) { Text(if (vm.activating) "در حال بررسی…" else "فعال‌سازی") }
    }
}


@Composable
private fun OfflineActivationForm(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.displayName) }
    var license by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val request = vm.offlineRequestCode

    Text("فعال‌سازی آفلاین", style = MaterialTheme.typography.headlineMedium)
    if (request == null) {
        Text("شناسهٔ دستگاه در دسترس نیست.", color = MaterialTheme.colorScheme.error)
        return
    }
    Text("کد درخواست این گوشی را برای پشتیبانی بفرستید و کد فعال‌سازی دریافتی را وارد کنید.")
    OutlinedTextField(
        value = request,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text("کد درخواست") },
        textStyle = TextStyle(textDirection = TextDirection.Ltr),
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { clipboard.setText(AnnotatedString(request)) }) { Text("کپی کد درخواست") }
    OutlinedTextField(name, { name = it }, label = { Text("نام شما") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        value = license,
        onValueChange = { license = it.take(128); invalid = false },
        label = { Text("کد فعال‌سازی") },
        singleLine = true,
        isError = invalid,
        textStyle = TextStyle(textDirection = TextDirection.Ltr),
        modifier = Modifier.fillMaxWidth(),
    )
    if (invalid) Text("کد فعال‌سازی برای این گوشی معتبر نیست.", color = MaterialTheme.colorScheme.error)
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = name.isNotBlank() && license.isNotBlank(),
        onClick = { invalid = !vm.activateOffline(name, license) },
    ) { Text("فعال‌سازی") }
}
