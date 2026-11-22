package ir.goldpayamak.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel

@Composable
fun AuthScreen(vm: AppViewModel) {
    val firstRun = !vm.isSetUp
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(if (firstRun) "راه‌اندازی اولیه" else "ورود", style = MaterialTheme.typography.headlineMedium)
        if (firstRun) {
            Text("این اطلاعات فقط روی همین گوشی ذخیره می‌شود. اگر رمز را فراموش کنید راه بازیابی وجود ندارد.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(name, { name = it }, label = { Text("نام شما (اول هر پیامک می‌آید)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(user, { user = it }, label = { Text("نام کاربری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("رمز عبور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (firstRun) {
            OutlinedTextField(pass2, { pass2 = it }, label = { Text("تکرار رمز عبور") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            if (firstRun) {
                error = when {
                    name.isBlank() -> "نام را وارد کنید"
                    user.isBlank() -> "نام کاربری را وارد کنید"
                    pass.length < 4 -> "رمز حداقل ۴ کاراکتر باشد"
                    pass != pass2 -> "رمز و تکرار آن یکسان نیست"
                    else -> null
                }
                if (error == null) vm.setup(name, user, pass)
            } else {
                if (!vm.login(user, pass)) error = "نام کاربری یا رمز اشتباه است"
            }
        }) { Text(if (firstRun) "ذخیره و ورود" else "ورود") }
    }
}
