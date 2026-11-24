package ir.goldpayamak.issuer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.goldpayamak.core.Jalali
import ir.goldpayamak.core.OfflineLicense
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IssuerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF805F10),
                primaryContainer = Color(0xFFF6EACD),
                surface = Color(0xFFFAF8F2),
            )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    IssuerScreen(
                        store = remember { IssuerStore(applicationContext) },
                        writePublicKey = { uri, key ->
                            contentResolver.openOutputStream(uri, "wt")?.use {
                                it.write(key.toByteArray(Charsets.US_ASCII))
                            } ?: error("Cannot open output file")
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssuerScreen(
    store: IssuerStore,
    writePublicKey: (android.net.Uri, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var request by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<IssuedLicense>()) }
    var result by remember { mutableStateOf<IssuedLicense?>(null) }
    var busy by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val latinText = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)

    fun copy(code: String) {
        clipboard.setText(AnnotatedString(code))
        feedback = "کد فعال‌سازی کپی شد."
    }

    LaunchedEffect(store) {
        try {
            entries = withContext(Dispatchers.IO) { store.list() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            feedback = "خواندن سوابق انجام نشد."
        } finally {
            busy = false
        }
    }

    val exportPublic = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { writePublicKey(uri, DeviceIssuer.publicKey()) }
                feedback = "کلید عمومی ذخیره شد."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                feedback = "ذخیرهٔ کلید عمومی انجام نشد."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("صدور لایسنس گلدپیامک") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(
                    onClick = { exportPublic.launch("goldpayamak-license-public.b64") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ذخیرهٔ کلید عمومی برای ساخت گلدپیامک") }
            }
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(100) },
                    label = { Text("نام مشتری") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = request, onValueChange = { request = it.take(80) },
                    label = { Text("کد درخواست گوشی مشتری") }, textStyle = latinText,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = request.isNotBlank() && !OfflineLicense.isRequestCode(request.trim()),
                )
            }
            item {
                Button(
                    enabled = !busy && name.isNotBlank() && OfflineLicense.isRequestCode(request.trim()),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val customer = name.trim()
                        val deviceRequest = request.trim()
                        busy = true
                        feedback = null
                        scope.launch {
                            try {
                                val issued = withContext(Dispatchers.IO) {
                                    store.record(customer, deviceRequest, DeviceIssuer.issue(deviceRequest))
                                }
                                entries = listOf(issued) + entries
                                result = issued
                                name = ""
                                request = ""
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                feedback = "صدور یا ذخیرهٔ کد انجام نشد."
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("ساخت کد فعال‌سازی") }
                feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            result?.let { issued ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("کد فعال‌سازی ${issued.name}", style = MaterialTheme.typography.titleMedium)
                            SelectionContainer { Text(issued.code, style = latinText) }
                            TextButton(onClick = { copy(issued.code) }) { Text("کپی کد فعال‌سازی") }
                        }
                    }
                }
            }
            item { Text("سوابق صدور", style = MaterialTheme.typography.titleLarge) }
            if (entries.isEmpty()) item { Text("هنوز کدی صادر نشده است.") }
            items(entries, key = { it.id }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text("${Jalali.fromMillis(entry.issuedAt).format()}  ${Jalali.timeOf(entry.issuedAt)}")
                        Text(entry.request, style = latinText)
                        TextButton(onClick = { copy(entry.code) }) { Text("کپی لایسنس") }
                    }
                }
            }
        }
    }
}
