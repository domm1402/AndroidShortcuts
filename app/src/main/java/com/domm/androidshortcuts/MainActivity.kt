package com.domm.androidshortcuts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Step(val type: String, val value: String = "")
data class Shortcut(val id: Long, val name: String, val steps: List<Step>, val tint: Int = 0)

private val accent = Color(0xFFB9F36A)
private val page = Color(0xFF10110F)
private val panel = Color(0xFF1C1E1A)
private val muted = Color(0xFF9B9F95)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("shortcuts", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var shortcuts by remember { mutableStateOf(loadShortcuts()) }
            var screen by remember { mutableStateOf("home") }
            var selected by remember { mutableStateOf<Shortcut?>(null) }
            var showCreate by remember { mutableStateOf(false) }
            var dark by remember { mutableStateOf(true) }
            val bg = if (dark) page else Color(0xFFF5F6F1)
            val card = if (dark) panel else Color.White
            val fg = if (dark) Color.White else Color(0xFF171914)
            MaterialTheme(colorScheme = darkColorScheme(
                background = bg, surface = card, onSurface = fg, primary = accent,
                onPrimary = Color(0xFF15180F), secondary = muted
            )) {
                Surface(Modifier.fillMaxSize(), color = bg) {
                    when (screen) {
                        "editor" -> EditorScreen(selected, onBack = { screen = "home" }, onSave = { item ->
                            shortcuts = (shortcuts.filterNot { it.id == item.id } + item)
                            saveShortcuts(shortcuts); selected = item; screen = "home"
                        }, onRun = { runShortcut(it) })
                        else -> HomeScreen(
                            shortcuts = shortcuts, dark = dark, onTheme = { dark = !dark },
                            onCreate = { selected = Shortcut(System.currentTimeMillis(), "New Shortcut", emptyList()); screen = "editor" },
                            onOpen = { selected = it; screen = "editor" },
                            onRun = { runShortcut(it) },
                            onGallery = { screen = "gallery" },
                            gallery = screen == "gallery",
                            onBack = { screen = "home" }
                        )
                    }
                }
            }
        }
    }

    private fun loadShortcuts(): List<Shortcut> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); val steps = o.getJSONArray("steps")
            Shortcut(o.getLong("id"), o.getString("name"), (0 until steps.length()).map { j ->
                val s = steps.getJSONObject(j); Step(s.getString("type"), s.optString("value"))
            })
        }
    }.getOrDefault(emptyList())

    private fun saveShortcuts(items: List<Shortcut>) {
        val arr = JSONArray()
        items.forEach { item ->
            val steps = JSONArray()
            item.steps.forEach { steps.put(JSONObject().put("type", it.type).put("value", it.value)) }
            arr.put(JSONObject().put("id", item.id).put("name", item.name).put("steps", steps))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun runShortcut(item: Shortcut) {
        var completed = 0
        item.steps.forEach { step ->
            try {
                when (step.type) {
                    "Open website" -> {
                        val url = if (step.value.startsWith("http")) step.value else "https://${step.value}"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    "Open app" -> {
                        val launch = packageManager.getLaunchIntentForPackage(step.value)
                        if (launch != null) startActivity(launch)
                        else Toast.makeText(this, "App package not found: ${step.value}", Toast.LENGTH_LONG).show()
                    }
                    "Show notification" -> Toast.makeText(this, step.value.ifBlank { item.name }, Toast.LENGTH_LONG).show()
                    "Copy to clipboard" -> {
                        val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("Shortcut", step.value))
                        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    "Share text" -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, step.value), "Share"))
                    "Wait" -> Thread.sleep((step.value.toLongOrNull() ?: 1L).coerceIn(0, 10) * 1000)
                }
                completed++
            } catch (_: Exception) { }
        }
        Toast.makeText(this, if (completed == item.steps.size) "Shortcut finished" else "Finished with some actions skipped", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun HomeScreen(shortcuts: List<Shortcut>, dark: Boolean, onTheme: () -> Unit, onCreate: () -> Unit, onOpen: (Shortcut) -> Unit, onRun: (Shortcut) -> Unit, onGallery: () -> Unit, gallery: Boolean, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(if (gallery) "Gallery" else "Shortcuts") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (gallery) "Discover" else "Shortcuts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(if (gallery) "Ideas to automate your day" else "Make your phone do more.", color = muted)
            }
            IconButton(onClick = onTheme) { Icon(Icons.Default.DarkMode, "Toggle theme") }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Shortcuts", "Gallery").forEach { label ->
                FilterChip(selected = (if (gallery) "Gallery" else "Shortcuts") == label, onClick = { if (label == "Gallery") onGallery() else onBack() }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!gallery) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MY SHORTCUTS", color = muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${shortcuts.size} shortcuts", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            if (shortcuts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Your automations start here", fontWeight = FontWeight.Bold)
                        Text("Build a shortcut from actions.", color = muted)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)) { Text("Create shortcut") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(shortcuts) { _, item ->
                        ShortcutCard(item, onOpen = { onOpen(item) }, onRun = { onRun(item) })
                    }
                }
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New shortcut")
                }
            }
        } else {
            Text("STARTER IDEAS", color = muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            val ideas = listOf("Morning setup" to "A place to collect your morning actions", "Quick share" to "Prepare text to share with another app", "Open a website" to "Launch your favourite website")
            ideas.forEach { (name, desc) ->
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = panel)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(desc, color = muted, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = onCreate) { Text("Build") }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Starter ideas are templates to customise, not preconfigured device automations.", color = muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 20.dp))
        }
    }
}

@Composable
fun ShortcutCard(item: Shortcut, onOpen: () -> Unit, onRun: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFF303D24), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, null, tint = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable { onOpen() }) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("${item.steps.size} actions", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, "Run shortcut", tint = accent) }
            IconButton(onClick = onOpen) { Icon(Icons.Default.ChevronRight, "Edit") }
        }
    }
}

@Composable
fun EditorScreen(initial: Shortcut?, onBack: () -> Unit, onSave: (Shortcut) -> Unit, onRun: (Shortcut) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "New Shortcut") }
    var steps by remember { mutableStateOf(initial?.steps ?: emptyList()) }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableIntStateOf(-1) }
    var value by remember { mutableStateOf("") }
    val types = listOf("Open website", "Open app", "Show notification", "Copy to clipboard", "Share text", "Wait")
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSave(Shortcut(initial?.id ?: System.currentTimeMillis(), name.ifBlank { "Untitled Shortcut" }, steps)) }) { Text("Save", color = accent) }
        }
        Text("Shortcut editor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shortcut name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIONS", color = muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${steps.size} added", color = muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        if (steps.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, null, tint = accent, modifier = Modifier.size(34.dp))
                    Text("Add your first action", fontWeight = FontWeight.Bold)
                    Text("Actions run in the order shown.", color = muted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(steps) { index, step ->
                    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = accent, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(step.type, fontWeight = FontWeight.Bold)
                                Text(step.value.ifBlank { "Tap to configure" }, color = muted, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { editing = index; value = step.value; picker = true }) { Icon(Icons.Default.Edit, "Edit action") }
                            IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.DeleteOutline, "Remove action") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { editing = -1; value = ""; picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(" Add action") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onRun(Shortcut(initial?.id ?: 0, name, steps)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)) { Icon(Icons.Default.PlayArrow, null); Text(" Run shortcut") }
    }
    if (picker) {
        AlertDialog(onDismissRequest = { picker = false }, title = { Text(if (editing >= 0) "Configure action" else "Choose an action") },
            text = {
                Column {
                    if (editing < 0) types.forEach { type ->
                        TextButton(onClick = { value = ""; editing = -2 - types.indexOf(type) }) { Text(type) }
                    } else {
                        Text(types.getOrNull(if (editing <= -2) -2 - editing else steps.getOrNull(editing)?.let { types.indexOf(it.type) } ?: 0) ?: steps.getOrNull(editing)?.type.orEmpty())
                        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value (URL, package name, text or seconds)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (editing >= 0 || editing <= -2) TextButton(onClick = {
                    val type = if (editing <= -2) types.getOrNull(-2 - editing) ?: "Show notification" else steps[editing].type
                    val next = steps.toMutableList()
                    val step = Step(type, value)
                    if (editing <= -2) next.add(step) else next[editing] = step
                    steps = next; picker = false; editing = -1
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { picker = false; editing = -1 }) { Text("Cancel") } }
        )
    }
}
