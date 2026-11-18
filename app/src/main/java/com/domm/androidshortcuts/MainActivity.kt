package com.domm.androidshortcuts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private val lime = Color(0xFFBDF477)
private val darkBg = Color(0xFF10120F)
private val darkCard = Color(0xFF1D211B)
private val soft = Color(0xFFA4AA9B)

data class ActionStep(val type: String, val value: String = "")
data class ShortcutItem(val id: Long, val name: String, val steps: List<ActionStep>, val trigger: String = "Manual")

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("shortcut_store", MODE_PRIVATE) }
    private val actionTypes = listOf(
        "Open website", "Open app", "Show message", "Copy to clipboard", "Share text",
        "Wait", "Open Android settings", "Open dialler", "Search the web", "Set an alarm"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var items by remember { mutableStateOf(readItems()) }
            var page by remember { mutableStateOf("Shortcuts") }
            var editing by remember { mutableStateOf<ShortcutItem?>(null) }
            var dark by remember { mutableStateOf(true) }
            val bg = if (dark) darkBg else Color(0xFFF4F5F0)
            val card = if (dark) darkCard else Color.White
            val fg = if (dark) Color.White else Color(0xFF171A14)
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = lime, background = bg, surface = card, onSurface = fg, onPrimary = Color(0xFF171A14)) else lightColorScheme(primary = Color(0xFF476B20), background = bg, surface = card, onSurface = fg)) {
                Surface(Modifier.fillMaxSize(), color = bg) {
                    if (editing != null) {
                        ShortcutEditor(editing!!, actionTypes, onBack = { editing = null }, onSave = { saved ->
                            items = (items.filterNot { it.id == saved.id } + saved)
                            writeItems(items); editing = null
                        }, onRun = { runItem(it) })
                    } else {
                        MainDashboard(
                            page = page, items = items, dark = dark, card = card,
                            onPage = { page = it }, onTheme = { dark = !dark },
                            onCreate = { editing = ShortcutItem(System.currentTimeMillis(), "New Shortcut", emptyList()) },
                            onEdit = { editing = it }, onRun = { runItem(it) },
                            onDelete = { item -> items = items.filterNot { it.id == item.id }; writeItems(items) },
                            onDuplicate = { item -> val copy = item.copy(id = System.currentTimeMillis(), name = item.name + " copy"); items = items + copy; writeItems(items) },
                            onExport = { exportItems(items) }, onImport = { raw -> importItems(raw)?.let { imported -> items = items + imported; writeItems(items); Toast.makeText(this, "Imported ${imported.size} shortcut(s)", Toast.LENGTH_SHORT).show() } ?: Toast.makeText(this, "That shortcut file could not be imported", Toast.LENGTH_LONG).show() }
                        )
                    }
                }
            }
        }
    }

    private fun readItems(): List<ShortcutItem> = runCatching {
        val a = JSONArray(prefs.getString("items", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val s = o.getJSONArray("steps")
            ShortcutItem(o.getLong("id"), o.getString("name"), (0 until s.length()).map { j ->
                val x = s.getJSONObject(j); ActionStep(x.getString("type"), x.optString("value"))
            }, o.optString("trigger", "Manual"))
        }
    }.getOrDefault(emptyList())

    private fun writeItems(items: List<ShortcutItem>) {
        val a = JSONArray()
        items.forEach { item ->
            val steps = JSONArray()
            item.steps.forEach { steps.put(JSONObject().put("type", it.type).put("value", it.value)) }
            a.put(JSONObject().put("id", item.id).put("name", item.name).put("trigger", item.trigger).put("steps", steps))
        }
        prefs.edit().putString("items", a.toString()).apply()
    }

    private fun exportItems(items: List<ShortcutItem>) {
        val text = JSONArray().also { a ->
            items.forEach { item ->
                val s = JSONArray(); item.steps.forEach { s.put(JSONObject().put("type", it.type).put("value", it.value)) }
                a.put(JSONObject().put("id", item.id).put("name", item.name).put("trigger", item.trigger).put("steps", s))
            }
        }.toString(2)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, text), "Export shortcuts"))
    }

    private fun importItems(raw: String): List<ShortcutItem>? = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val s = o.getJSONArray("steps")
            ShortcutItem(System.currentTimeMillis() + i, o.optString("name", "Imported shortcut"), (0 until s.length()).map { j ->
                val x = s.getJSONObject(j); ActionStep(x.getString("type"), x.optString("value"))
            }, o.optString("trigger", "Manual"))
        }
    }.getOrNull()

    private fun runItem(item: ShortcutItem) {
        lifecycleScope.launch {
            var failed = 0
            item.steps.forEach { step ->
                try {
                    when (step.type) {
                        "Open website" -> {
                            val address = step.value.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
                        }
                        "Open app" -> {
                            val launch = packageManager.getLaunchIntentForPackage(step.value.trim())
                            if (launch != null) startActivity(launch) else throw IllegalArgumentException("App package not found")
                        }
                        "Show message" -> Toast.makeText(this@MainActivity, step.value.ifBlank { item.name }, Toast.LENGTH_LONG).show()
                        "Copy to clipboard" -> {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut", step.value))
                            Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
                        }
                        "Share text" -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, step.value), "Share"))
                        "Wait" -> kotlinx.coroutines.delay((step.value.toLongOrNull() ?: 1L).coerceIn(0, 30) * 1000)
                        "Open Android settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
                        "Open dialler" -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(step.value)}")))
                        "Search the web" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(step.value)}")))
                        "Set an alarm" -> startActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, step.value.ifBlank { item.name }).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false))
                    }
                } catch (_: Exception) { failed++ }
            }
            Toast.makeText(this@MainActivity, if (failed == 0) "Shortcut finished" else "$failed action(s) could not run", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun MainDashboard(
    page: String, items: List<ShortcutItem>, dark: Boolean, card: Color,
    onPage: (String) -> Unit, onTheme: () -> Unit, onCreate: () -> Unit,
    onEdit: (ShortcutItem) -> Unit, onRun: (ShortcutItem) -> Unit,
    onDelete: (ShortcutItem) -> Unit, onDuplicate: (ShortcutItem) -> Unit,
    onExport: () -> Unit, onImport: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    val filtered = items.filter { it.name.contains(search, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shortcuts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text("Automate the little things.", color = soft)
            }
            IconButton(onClick = onTheme) { Icon(Icons.Default.Contrast, "Toggle theme") }
        }
        Spacer(Modifier.height(18.dp))
        NavigationBar(containerColor = card, tonalElevation = 0.dp) {
            listOf("Shortcuts" to Icons.Default.Bolt, "Gallery" to Icons.Default.AutoAwesome, "Automations" to Icons.Default.Schedule, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                NavigationBarItem(selected = page == label, onClick = { onPage(label) }, icon = { Icon(icon, null) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(16.dp))
        when (page) {
            "Shortcuts" -> {
                OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search shortcuts") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ALL SHORTCUTS", color = soft, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${items.size} saved", color = soft, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Bolt, null, tint = lime, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(if (items.isEmpty()) "Nothing here yet" else "No matching shortcuts", fontWeight = FontWeight.Bold)
                            Text("Build a flow from individual actions.", color = soft)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onCreate) { Icon(Icons.Default.Add, null); Text(" Create shortcut") }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { item ->
                            var menu by remember { mutableStateOf(false) }
                            Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(45.dp).background(Color(0xFF344525), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = lime) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f).clickable { onEdit(item) }) {
                                        Text(item.name, fontWeight = FontWeight.Bold)
                                        Text("${item.steps.size} actions · ${item.trigger}", color = soft, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { onRun(item) }) { Icon(Icons.Default.PlayArrow, "Run", tint = lime) }
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(item) })
                                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate(item) })
                                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(item) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                    Button(onClick = onCreate, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" New shortcut") }
                    OutlinedButton(onClick = onExport) { Icon(Icons.Default.Upload, null); Text(" Export") }
                    OutlinedButton(onClick = { showImport = true }) { Icon(Icons.Default.Download, null); Text(" Import") }
                }
            }
            "Gallery" -> {
                Text("STARTER GALLERY", color = soft, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                val ideas = listOf("Quick website" to "Open a favourite website in one tap.", "Share a message" to "Keep a reusable message ready to share.", "App launcher" to "Open an app using its package name.", "Bedtime helper" to "Chain a wait, message and settings shortcut.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ideas) { (title, desc) ->
                        Card(colors = CardDefaults.cardColors(containerColor = card)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = lime, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(desc, color = soft, style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = onCreate) { Text("Use") }
                            }
                        }
                    }
                }
            }
            "Automations" -> {
                Text("AUTOMATIONS", color = soft, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = card)) {
                    Column(Modifier.padding(18.dp)) {
                        Icon(Icons.Default.Schedule, tint = lime, contentDescription = null, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Trigger-based automations", fontWeight = FontWeight.Bold)
                        Text("Shortcuts can currently be launched manually. Android background schedules, location triggers and event listeners need additional permissions and system-specific handling.", color = soft)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onCreate) { Text("Build a shortcut") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                items.filter { it.trigger != "Manual" }.forEach { Text(it.name + " · " + it.trigger, color = soft) }
            }
            else -> {
                Text("PREFERENCES", color = soft, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = card)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DarkMode, null); Spacer(Modifier.width(12.dp)); Text("Dark appearance", Modifier.weight(1f)); Switch(checked = dark, onCheckedChange = { onTheme() }) }
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text("Shortcuts are stored locally on this device.", color = soft)
                        Text("Some actions open Android's own confirmation screen. Protected system settings cannot be changed silently by ordinary apps.", color = soft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showImport) AlertDialog(
        onDismissRequest = { showImport = false },
        title = { Text("Import shortcuts") },
        text = { Column { Text("Paste a JSON export below."); Spacer(Modifier.height(8.dp)); OutlinedTextField(importText, { importText = it }, minLines = 4, placeholder = { Text("[ ... ]") }) } },
        confirmButton = { TextButton(onClick = { onImport(importText); importText = ""; showImport = false }) { Text("Import") } },
        dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
    )
}

@Composable
private fun ShortcutEditor(item: ShortcutItem, actionTypes: List<String>, onBack: () -> Unit, onSave: (ShortcutItem) -> Unit, onRun: (ShortcutItem) -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var trigger by remember(item.id) { mutableStateOf(item.trigger) }
    var steps by remember(item.id) { mutableStateOf(item.steps) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSave(ShortcutItem(item.id, name.ifBlank { "Untitled Shortcut" }, steps, trigger)) }) { Text("Save", color = lime) }
        }
        Text("Build shortcut", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Shortcut name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Text("WHEN SHOULD IT RUN?", color = soft, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Manual", "On a schedule").forEach { option ->
                FilterChip(selected = trigger == option, onClick = { trigger = option }, label = { Text(option) })
            }
        }
        if (trigger == "On a schedule") Text("Scheduling is a planned feature; this shortcut remains manually runnable in this build.", color = soft, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIONS", color = soft, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${steps.size} steps", color = soft)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(steps) { index, step ->
                Card(colors = CardDefaults.cardColors(containerColor = darkCard), shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = lime, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(step.type, fontWeight = FontWeight.Bold); Text(step.value.ifBlank { "No value" }, color = soft, style = MaterialTheme.typography.bodySmall) }
                        IconButton(onClick = { editingIndex = index; chosen = step.type; value = step.value }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.DeleteOutline, "Delete") }
                        IconButton(enabled = index > 0, onClick = { steps = steps.toMutableList().also { val x = it.removeAt(index); it.add(index - 1, x) } }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(enabled = index < steps.lastIndex, onClick = { steps = steps.toMutableList().also { val x = it.removeAt(index); it.add(index + 1, x) } }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { editingIndex = -1; chosen = ""; value = "" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(" Add action") }
            }
        }
        Button(onClick = { onRun(ShortcutItem(item.id, name, steps, trigger)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Icon(Icons.Default.PlayArrow, null); Text(" Run shortcut") }
    }
    if (chosen != null) {
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(if (chosen!!.isBlank()) "Choose action" else "Configure action") },
            text = {
                Column {
                    if (chosen!!.isBlank()) {
                        actionTypes.forEach { type -> TextButton(onClick = { chosen = type }) { Text(type) } }
                    } else {
                        Text(chosen!!, color = soft)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value, { value = it }, label = { Text(when (chosen) {
                            "Open website" -> "Website address"
                            "Open app" -> "Android package name"
                            "Wait" -> "Seconds (0 to 30)"
                            "Set an alarm" -> "Alarm label"
                            "Open dialler" -> "Phone number (optional)"
                            else -> "Text or search query"
                        }) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (chosen!!.isNotBlank()) TextButton(onClick = {
                    val step = ActionStep(chosen!!, value)
                    steps = steps.toMutableList().also { if (editingIndex >= 0) it[editingIndex] = step else it.add(step) }
                    chosen = null; editingIndex = -1
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { chosen = null; editingIndex = -1 }) { Text("Cancel") } }
        )
    }
}
