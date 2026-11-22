package ir.goldpayamak.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.goldpayamak.app.data.ContactEntity
import ir.goldpayamak.core.TextNorm

/** انتخاب از مخاطبین برنامه با جستجو (نام یا شماره). */
@Composable
fun AppContactPickerDialog(contacts: List<ContactEntity>, onPick: (ContactEntity) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = TextNorm.fa(query)
    val list = remember(contacts, q) {
        if (q.isEmpty()) contacts else contacts.filter { TextNorm.fa(it.name).contains(q) || it.phone.contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب از مخاطبین برنامه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("جستجوی نام یا شماره") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when {
                    contacts.isEmpty() -> Text("هنوز مخاطبی ذخیره نشده است.")
                    list.isEmpty() -> Text("موردی پیدا نشد.")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list, key = { it.id }) { c ->
                            TextButton(onClick = { onPick(c) }, Modifier.fillMaxWidth()) { Text("${c.name}  ${c.phone}", Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
