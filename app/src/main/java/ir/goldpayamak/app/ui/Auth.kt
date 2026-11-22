package ir.goldpayamak.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.app.data.ActivationResult
import ir.goldpayamak.core.Digits

const val CODE_LENGTH = 12

/** فقط رقم (فارسی/عربی هم پذیرفته و به لاتین تبدیل می‌شود)، حداکثر ۱۲ رقم */
private fun cleanCode(s: String) = Digits.toLatin(s).filter { it in '0'..'9' }.take(CODE_LENGTH)

/** فعال‌سازی یک‌باره با کد ۱۲ رقمی یک‌بارمصرف. بعد از موفقیت دیگر نمایش داده نمی‌شود. */
@Composable
fun AuthScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.displayName) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
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
