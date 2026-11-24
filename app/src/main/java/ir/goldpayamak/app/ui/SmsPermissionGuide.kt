package ir.goldpayamak.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.goldpayamak.app.R

private data class GuideScreenshot(val image: Int, val caption: String)

private val appInfo = GuideScreenshot(R.drawable.sms_guide_app_info, "اطلاعات برنامه و گزینهٔ Permissions")
private val permissions = GuideScreenshot(R.drawable.sms_guide_permissions, "گزینهٔ SMS در فهرست مجوزها")
private val allow = GuideScreenshot(R.drawable.sms_guide_allow, "انتخاب Allow در صفحهٔ SMS permission")
private val allowed = GuideScreenshot(R.drawable.sms_guide_allowed, "نمونهٔ مجوز پیامک پس از انتخاب Allow")
private val restricted = GuideScreenshot(R.drawable.sms_guide_restricted, "منوی سه‌نقطه و گزینهٔ Allow restricted settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsPermissionHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showRestricted by rememberSaveable { mutableStateOf(false) }
    var screenshot by remember { mutableStateOf<GuideScreenshot?>(null) }
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("راهنمای فعال کردن پیامک") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (settingsUnavailable) {
                            Text("در تنظیمات گوشی، «برنامه‌ها ← گلدپیامک» را باز کنید.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)))
                                } catch (_: ActivityNotFoundException) {
                                    settingsUnavailable = true
                                }
                            },
                        ) { Text("باز کردن تنظیمات برنامه") }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("تصاویر از گوشی سامسونگ هستند. برای بزرگ‌نمایی، روی تصویر بزنید.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    GuideCard("۱. اطلاعات گلدپیامک", "دکمهٔ پایین صفحه را بزنید. در صفحهٔ «اطلاعات برنامه» گزینهٔ Permissions را باز کنید.",
                        listOf(appInfo), onImage = { screenshot = it })
                }
                item {
                    GuideCard("۲. مجوز پیامک", "روی SMS بزنید و Allow را انتخاب کنید. پس از فعال شدن مجوز، به گلدپیامک برگردید و ارسال را دوباره تأیید کنید.",
                        listOf(permissions, allow, allowed), onImage = { screenshot = it })
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("اگر اندروید دسترسی را محدود کرد", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showRestricted = !showRestricted }) {
                                Text(if (showRestricted) "بستن مرحلهٔ اختیاری" else "دیدن مرحلهٔ سه‌نقطه")
                            }
                            if (showRestricted) {
                                Text("اگر گزینهٔ Allow restricted settings در منوی سه‌نقطهٔ «اطلاعات برنامه» دیده می‌شود و به نسخهٔ نصب‌شده اعتماد دارید، آن را انتخاب کنید. بعد دوباره Permissions ← SMS را باز کنید و Allow را بزنید.")
                                GuideImage(restricted, onImage = { screenshot = it })
                            }
                        }
                    }
                }
            }
        }
    }

    screenshot?.let { selected ->
        Dialog(onDismissRequest = { screenshot = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                    TextButton(onClick = { screenshot = null }) { Text("بستن تصویر") }
                    Image(
                        painterResource(selected.image),
                        contentDescription = selected.caption,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideCard(title: String, description: String, images: List<GuideScreenshot>, onImage: (GuideScreenshot) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            images.forEach { GuideImage(it, onImage) }
        }
    }
}

@Composable
private fun GuideImage(screenshot: GuideScreenshot, onImage: (GuideScreenshot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(
            painterResource(screenshot.image),
            contentDescription = screenshot.caption,
            modifier = Modifier.fillMaxWidth().height(230.dp)
                .clickable(onClickLabel = "بزرگ کردن تصویر") { onImage(screenshot) },
            contentScale = ContentScale.Fit,
        )
        Text(screenshot.caption, style = MaterialTheme.typography.labelMedium)
    }
}
