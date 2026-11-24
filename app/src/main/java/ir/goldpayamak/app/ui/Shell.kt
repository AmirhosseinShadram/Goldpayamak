@file:OptIn(ExperimentalMaterial3Api::class)

package ir.goldpayamak.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ir.goldpayamak.app.data.RequestEntity
import ir.goldpayamak.app.data.Status
import ir.goldpayamak.app.statusLabel
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import ir.goldpayamak.app.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import ir.goldpayamak.core.Jalali
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import ir.goldpayamak.app.AppViewModel

sealed interface Screen {
    data class Form(val parentId: Long?) : Screen
    data class GoldForm(val parentId: Long?) : Screen
    data class Detail(val requestId: Long) : Screen
    data class ContactHistory(val name: String, val phone: String) : Screen
}

private val tabs = listOf("خانه", "حواله‌ها", "طلایی", "گزارش", "بیشتر")
@Composable
fun MainShell(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var morePage by remember { mutableStateOf<String?>(null) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    BackHandler(enabled = stack.isEmpty() && morePage != null) { morePage = null }

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
        topBar = {
            TopAppBar(
                title = {
                    if (tab == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(painterResource(R.drawable.gold_bars), null, Modifier.size(width = 50.dp, height = 35.dp))
                            Text("گلدپیامک", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    } else Text(morePage ?: tabs[tab])
                },
                actions = {
                    if (tab == 0) IconButton(onClick = { tab = 3 }) {
                        Icon(Icons.Outlined.Notifications, "وضعیت ارسال")
                    }
                },
                navigationIcon = {
                    if (morePage != null) IconButton(onClick = { morePage = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                },
            )
        },
        bottomBar = {
            HomeNavigation(tab) { index -> tab = index; morePage = null }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> NewRequestEntry(
                    vm = vm,
                    onStart = { push(Screen.Form(null)) },
                    onGold = { push(Screen.GoldForm(null)) },
                    onHistory = { tab = 1 },
                    onOpen = { push(Screen.Detail(it)) },
                )
                1 -> HistoryScreen(vm) { push(Screen.Detail(it)) }
                2 -> GoldEntry(vm) { push(Screen.GoldForm(null)) }
                3 -> ReportScreen(vm)
                else -> when (morePage) {
                    "مخاطبین" -> ContactsScreen(vm) { n, p -> push(Screen.ContactHistory(n, p)) }
                    "تنظیمات" -> SettingsScreen(vm)
                    else -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedCard(onClick = { morePage = "مخاطبین" }) {
                            ListItem(headlineContent = { Text("مخاطبین") }, leadingContent = { Icon(Icons.Filled.Person, null) })
                        }
                        OutlinedCard(onClick = { morePage = "تنظیمات" }) {
                            ListItem(headlineContent = { Text("تنظیمات") }, leadingContent = { Icon(Icons.Filled.Settings, null) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoldEntry(vm: AppViewModel, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("پیامک «بابت ${vm.defaultSenderName} فروختم/خریدم» برای یک نفر، با مبلغ و فی طلایی.")
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("ثبت طلایی جدید") }
    }
}

@Composable
private fun HomeNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 5.dp, vertical = 5.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val active = selected == index
                val itemShape = RoundedCornerShape(18.dp)
                Surface(
                    modifier = Modifier.weight(1f).clip(itemShape).selectable(active, role = Role.Tab) { onSelect(index) },
                    shape = itemShape,
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        Modifier.heightIn(min = 62.dp).padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        when (index) {
                            0 -> Icon(Icons.Outlined.Home, null)
                            1 -> Icon(painterResource(R.drawable.ic_transfer), null)
                            2 -> Icon(painterResource(R.drawable.ic_gold_trade), null)
                            3 -> Icon(painterResource(R.drawable.ic_report_bars), null)
                            else -> Icon(Icons.Filled.MoreVert, null, Modifier.rotate(90f))
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewRequestEntry(
    vm: AppViewModel,
    onStart: () -> Unit,
    onGold: () -> Unit,
    onHistory: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val roots by vm.roots.collectAsState()
    val actions by vm.actions.collectAsState()
    val recent = roots.filter { it.type == "RING" }.take(3)
    val chains = actions.groupBy { it.rootId }
    val actionShape = RoundedCornerShape(14.dp)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EFE3)),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 108.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("سلام", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("به گلدپیامک خوش آمدید", style = MaterialTheme.typography.bodyMedium)
                    }
                    Image(painterResource(R.drawable.gold_bars), null, modifier = Modifier.width(118.dp))
                }
            }
        }
        item {
            Button(onClick = onStart, shape = actionShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(10.dp))
                Text("ثبت حواله جدید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick = onGold,
                shape = actionShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(min = 52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_gold_trade), null)
                Spacer(Modifier.width(10.dp))
                Text("ثبت طلایی", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, null)
                    Text("آخرین حواله‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onHistory) { Text("مشاهده همه ‹") }
            }
        }
        if (recent.isEmpty()) item {
            Text("هنوز حواله‌ای ثبت نشده است.", Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recent, key = { it.id }) { root ->
            val latest = chains[root.id]?.lastOrNull() ?: root
            HomeTransferCard(vm, root.id, latest) { onOpen(root.id) }
        }
    }
}

@Composable
private fun HomeTransferCard(vm: AppViewModel, rootId: Long, latest: RequestEntity, onOpen: () -> Unit) {
    val messages by remember(rootId) { vm.dao.messagesOfRoot(rootId) }.collectAsState(initial = emptyList())
    val recipients = messages.filter { it.requestId == latest.id && it.status != Status.SELF }
    val activeRecipients = if (latest.kind == "CORRECTION") recipients.filter { it.kind != "CANCEL" } else recipients
    val status = when {
        latest.kind == "CANCEL" -> Status.REQ_CANCELLED
        recipients.any { it.status == Status.FAILED } -> Status.FAILED
        recipients.any { it.status == Status.SENDING } -> Status.SENDING
        recipients.any { it.status == Status.PENDING } -> Status.PENDING
        recipients.isNotEmpty() && recipients.all { it.status == Status.DELIVERED } -> Status.DELIVERED
        recipients.isNotEmpty() && recipients.all { it.status == Status.SENT || it.status == Status.DELIVERED } -> Status.SENT
        else -> Status.REQ_RECORD
    }
    val color = if (status == Status.DELIVERED) Color(0xFF246B3C) else statusColor(status)
    val today = LocalDate.now(Jalali.TEHRAN)
    val day = Jalali.fromMillis(latest.createdAt)
    val date = when (day) {
        Jalali.fromLocalDate(today) -> "امروز"
        Jalali.fromLocalDate(today.minusDays(1)) -> "دیروز"
        else -> day.format()
    }
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFCFAF5))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(14.dp))
            Icon(Icons.Filled.Person, null, Modifier.size(18.dp))
            Text(activeRecipients.firstOrNull()?.name.orEmpty(), Modifier.weight(0.85f), style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(latest.transferAmount.format(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("$date ${Jalali.timeOf(latest.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(9.dp)) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (status == Status.DELIVERED || status == Status.FAILED) {
                        Icon(if (status == Status.DELIVERED) Icons.Filled.Check else Icons.Filled.Close, null, Modifier.size(14.dp))
                    }
                    Text(statusLabel(status), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
