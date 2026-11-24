@file:OptIn(ExperimentalMaterial3Api::class)

package ir.goldpayamak.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.AppViewModel

sealed interface Screen {
    data class Form(val parentId: Long?) : Screen
    data class GoldForm(val parentId: Long?) : Screen
    data class Detail(val requestId: Long) : Screen
    data class ContactHistory(val name: String, val phone: String) : Screen
}

private val tabs = listOf("حواله", "طلایی", "تاریخچه", "مخاطبین", "گزارش", "تنظیمات")
private val tabIcons: List<ImageVector> = listOf(
    Icons.Filled.AddCircle, Icons.Filled.Star, Icons.AutoMirrored.Filled.List, Icons.Filled.Person, Icons.Filled.DateRange, Icons.Filled.Settings,
)

@Composable
fun MainShell(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val top = stack.lastOrNull()
    if (top != null) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(when (top) {
                    is Screen.Form -> if (top.parentId == null) "حواله جدید" else "اصلاحیه حواله"
                    is Screen.GoldForm -> if (top.parentId == null) "طلایی جدید" else "اصلاحیه طلایی"
                    is Screen.Detail -> "جزئیات درخواست"
                    is Screen.ContactHistory -> top.name
                }) },
                navigationIcon = { IconButton(onClick = { pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
            )
        }) { pad ->
            Box(Modifier.padding(pad)) {
                when (top) {
                    is Screen.Form -> FormScreen(vm, top.parentId, onDone = { id ->
                        stack = if (top.parentId == null) listOf(Screen.Detail(id)) else listOf(Screen.Detail(id))
                    })
                    is Screen.GoldForm -> GoldForm(vm, top.parentId, onDone = { id -> stack = listOf(Screen.Detail(id)) })
                    is Screen.Detail -> DetailScreen(vm, top.requestId,
                        onCorrect = { id, gold -> push(if (gold) Screen.GoldForm(id) else Screen.Form(id)) },
                        onOpen = { stack = stack.dropLast(1) + Screen.Detail(it) })
                    is Screen.ContactHistory -> ContactHistoryScreen(vm, top.phone)
                }
            }
        }
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs[tab]) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(tabIcons[i], contentDescription = t) }, label = { Text(t, maxLines = 1, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> NewRequestEntry(vm) { push(Screen.Form(null)) }
                1 -> GoldEntry(vm) { push(Screen.GoldForm(null)) }
                2 -> HistoryScreen(vm) { push(Screen.Detail(it)) }
                3 -> ContactsScreen(vm) { n, p -> push(Screen.ContactHistory(n, p)) }
                4 -> ReportScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun GoldEntry(vm: AppViewModel, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)) {
        Text("طلایی", style = MaterialTheme.typography.headlineSmall)
        Text("پیامک «بابت ${vm.defaultSenderName} فروختم/خریدم» برای یک نفر، با مبلغ و فی طلایی.")
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("ثبت طلایی جدید") }
    }
}

@Composable
private fun NewRequestEntry(vm: AppViewModel, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)) {
        Text("سلام ${vm.displayName}", style = MaterialTheme.typography.headlineSmall)
        Text("طرف‌ها و قیمت را وارد کنید تا پیامک برای همهٔ طرف‌ها از سیم‌کارت همین گوشی ارسال و در گزارش شما ثبت شود.")
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("ثبت حواله جدید") }
    }
}
