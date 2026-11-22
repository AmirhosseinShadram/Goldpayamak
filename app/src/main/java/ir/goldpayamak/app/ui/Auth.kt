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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel
import ir.goldpayamak.core.Digits

const val CODE_LENGTH = 12

/** فقط رقم (فارسی/عربی هم پذیرفته و به لاتین تبدیل می‌شود)، حداکثر ۱۲ رقم */
private fun cleanCode(s: String) = Digits.toLatin(s).filter { it in '0'..'9' }.take(CODE_LENGTH)

@Composable
fun AuthScreen(vm: AppViewModel) {
    val firstRun = !vm.isSetUp
    var name by remember { mutableStateOf(vm.displayName) }
    var code by remember { mutableStateOf("") }
    var code2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(if (firstRun) "راه‌اندازی اولیه" else "ورود", style = MaterialTheme.typography.headlineMedium)
        if (firstRun) {
            Text("یک کد ۱۲ رقمی برای ورود به برنامه انتخاب کنید. کد فقط روی همین گوشی ذخیره می‌شود و راه بازیابی ندارد؛ آن را جایی یادداشت کنید.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(name, { name = it }, label = { Text("نام شما (اول هر پیامک می‌آید)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(code, { code = cleanCode(it) }, label = { Text(if (firstRun) "کد ۱۲ رقمی" else "کد ورود ۱۲ رقمی") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            supportingText = { Text("${Digits.toPersian(code.length.toString())} از ${Digits.toPersian(CODE_LENGTH.toString())}") },
            modifier = Modifier.fillMaxWidth())
        if (firstRun) {
            OutlinedTextField(code2, { code2 = cleanCode(it) }, label = { Text("تکرار کد") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), enabled = code.length == CODE_LENGTH, onClick = {
            if (firstRun) {
                error = when {
                    name.isBlank() -> "نام را وارد کنید"
                    code != code2 -> "کد و تکرار آن یکسان نیست"
                    else -> null
                }
                if (error == null) vm.setup(name, code)
            } else {
                val wait = vm.lockoutSeconds()
                error = if (wait > 0) "تلاش‌های ناموفق زیاد بود؛ ${Digits.toPersian(wait.toString())} ثانیه صبر کنید"
                else if (!vm.login(code)) {
                    code = ""
                    if (vm.lockoutSeconds() > 0) "کد اشتباه است؛ به‌دلیل تلاش‌های زیاد ۳۰ ثانیه قفل شد" else "کد اشتباه است"
                } else null
            }
        }) { Text(if (firstRun) "ذخیره و ورود" else "ورود") }
    }
}
