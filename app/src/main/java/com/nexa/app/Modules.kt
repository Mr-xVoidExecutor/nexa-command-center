package com.nexa.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reusable "list of entries" editor. Items are stored encrypted as "a<TAB>b". */
@Composable
fun Entries(
    key: String, la: String, lb: String, secret: Boolean = false, linesB: Int = 1,
    action: @Composable (String, String) -> Unit = { _, _ -> }
) {
    var items by remember { mutableStateOf(Store.list(key)) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Field(a, { a = it }, la)
    Field(b, { b = it }, lb, secret = secret, lines = linesB)
    NeonButton("ADD", Modifier.fillMaxWidth()) {
        if (a.isNotBlank()) {
            items = items + "${a.trim()}\t${b.trim()}"
            Store.saveList(key, items)
            a = ""; b = ""
        }
    }
    Spacer(Modifier.height(6.dp))
    items.forEachIndexed { i, s ->
        val p = s.split("\t", limit = 2)
        val v = p.getOrElse(1) { "" }
        Panel {
            Mono(p[0], Cyan, 14, true)
            Mono(if (secret) "••••••••" else v, Dim, 12)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                action(p[0], v)
                Link("DELETE", Pink) {
                    items = items.filterIndexed { j, _ -> j != i }
                    Store.saveList(key, items)
                }
            }
        }
    }
}

@Composable
fun GithubScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf(listOf<String>()) }
    var name by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    Screen("GitHub", back) {
        if (Store.get("ghToken").isEmpty()) Mono("Connect GitHub in Accounts first.", Pink)
        NeonButton("REFRESH REPOS", Modifier.fillMaxWidth()) { scope.launch { repos = Api.repos() } }
        Field(name, { name = it }, "New repo name")
        NeonButton("CREATE PRIVATE REPO", Modifier.fillMaxWidth()) {
            scope.launch { info = Api.createRepo(name.trim()); repos = Api.repos() }
        }
        if (info.isNotEmpty()) Panel { Mono(info, Cyan) }
        Mono("Tap a repo to see its latest Actions builds", Dim, 11)
        repos.forEach { r ->
            Panel(Modifier.clickable {
                scope.launch { info = "Loading…"; info = Api.runs(r.substringAfter(" ")) }
            }) { Mono(r) }
        }
    }
}

@Composable
fun DeployScreen(back: () -> Unit) {
    Screen("Deploy / Go Live", back) {
        Mono("Connect Vercel or Netlify in Accounts. You can also add a deploy hook URL and fire it to push your site live.", Dim, 12)
        Spacer(Modifier.height(6.dp))
        Entries("hooks", "Site name", "Deploy hook URL") { n, _ ->
            val sc = rememberCoroutineScope()
            var r by remember { mutableStateOf("") }
            Link("🚀 FIRE") { sc.launch { r = "…"; r = Api.deploy(n) } }
            Mono(r, Cyan, 11)
        }
    }
}

@Composable
fun DbScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Store.get("supaUrl")) }
    var key by remember { mutableStateOf(Store.get("supaKey")) }
    var table by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    Screen("Database (Supabase)", back) {
        if (Store.get("supaUrl").isBlank() || Store.get("supaKey").isBlank()) {
            Mono("Connect Supabase and save the project URL/key in Accounts first.", Pink, 11)
        }
        Field(url, { url = it }, "Project URL (https://xxxx.supabase.co)")
        Field(key, { key = it }, "API key", secret = true)
        NeonButton("SAVE CONNECTION", Modifier.fillMaxWidth()) {
            Store.put("supaUrl", url.trim()); Store.put("supaKey", key.trim()); out = "Saved."
        }
        Field(table, { table = it }, "Table name")
        NeonButton("QUERY (first 20 rows)", Modifier.fillMaxWidth()) {
            scope.launch { out = "Loading…"; out = Api.table(table) }
        }
        if (out.isNotEmpty()) Panel { Mono(out, Ink, 11) }
    }
}

@Composable
fun AdsScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var accts by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var info by remember { mutableStateOf("") }
    Screen("Meta Ads", back) {
        if (Store.get("fbToken").isEmpty()) Mono("Add your Meta access token in Settings (needs ads_read).", Pink)
        NeonButton("LOAD AD ACCOUNTS", Modifier.fillMaxWidth()) { scope.launch { accts = Api.adAccounts() } }
        if (info.isNotEmpty()) Panel { Mono(info, Cyan) }
        accts.forEach { (id, label) ->
            Panel(Modifier.clickable {
                if (id.isNotEmpty()) scope.launch { info = "Loading…"; info = label + "\nTODAY: " + Api.adInsights(id) }
            }) {
                Mono(label)
                Mono("tap for today's insights", Dim, 11)
            }
        }
    }
}

@Composable
fun TasksScreen(back: () -> Unit) {
    var items by remember { mutableStateOf(Store.list("tasks")) }
    var t by remember { mutableStateOf("") }
    fun save(l: List<String>) { items = l; Store.saveList("tasks", l) }
    Screen("Tasks", back) {
        Field(t, { t = it }, "New task")
        NeonButton("ADD TASK", Modifier.fillMaxWidth()) {
            if (t.isNotBlank()) { save(items + "0\t${t.trim()}"); t = "" }
        }
        items.forEachIndexed { i, s ->
            val done = s.startsWith("1")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(done, {
                        save(items.mapIndexed { j, x -> if (j == i) (if (done) "0" else "1") + x.drop(1) else x })
                    })
                    Text(
                        s.substringAfter("\t"),
                        color = if (done) Dim else Ink,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                    Text("✕", color = Pink, modifier = Modifier.clickable {
                        save(items.filterIndexed { j, _ -> j != i })
                    }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
fun NotesScreen(back: () -> Unit) {
    val clip = LocalClipboardManager.current
    Screen("Notes", back) {
        Entries("notes", "Title", "Note", linesB = 4) { _, v ->
            Link("COPY") { clip.setText(AnnotatedString(v)) }
        }
    }
}

@Composable
fun VaultScreen(back: () -> Unit) {
    val clip = LocalClipboardManager.current
    Screen("Secure Vault", back) {
        Mono("Encrypted with the Android Keystore. Store passwords, API keys, tokens.", Dim, 12)
        Spacer(Modifier.height(6.dp))
        Entries("vault", "Label", "Secret", secret = true) { _, v ->
            Link("COPY") { clip.setText(AnnotatedString(v)) }
        }
    }
}

@Composable
fun UptimeScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var out by remember { mutableStateOf("") }
    Screen("Website Uptime", back) {
        NeonButton("CHECK ALL & SPEAK", Modifier.fillMaxWidth()) {
            scope.launch { out = "Checking…"; out = Api.checkAll(); Voice.say(out.replace("\n", ". ")) }
        }
        if (out.isNotEmpty()) Panel { Mono(out, Cyan) }
        Entries("sites", "Name", "https://example.com") { _, u ->
            val sc = rememberCoroutineScope()
            var r by remember(u) { mutableStateOf("") }
            Link("CHECK") { sc.launch { r = "…"; r = Api.ping(u) } }
            Mono(r, Cyan, 11)
        }
    }
}

@Composable
fun ConsoleScreen(back: () -> Unit, lock: () -> Unit) {
    val scope = rememberCoroutineScope()
    val log = remember { mutableStateListOf("NEXA console ready. Type 'help'.") }
    var cmd by remember { mutableStateOf("") }
    Screen("Console", back) {
        Panel { log.forEach { Mono(it, if (it.startsWith(">")) Cyan else Ink, 12) } }
        Field(cmd, { cmd = it }, "command")
        NeonButton("RUN", Modifier.fillMaxWidth()) {
            val c = cmd.trim()
            cmd = ""
            if (c == "clear") log.clear()
            else if (c.isNotEmpty()) {
                log.add("> $c")
                scope.launch {
                    val o = Commands.run(c)
                    if (o == "LOCK") lock() else log.add(o)
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(back: () -> Unit) {
    val clip = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    Screen("Tools", back) {
        Field(input, { input = it }, "Input text / JSON / password length", lines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton("SHA256", Modifier.weight(1f)) { out = Tools.sha256(input) }
            NeonButton("JSON", Modifier.weight(1f)) { out = Tools.json(input) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton("B64 ENC", Modifier.weight(1f)) { out = Tools.b64e(input) }
            NeonButton("B64 DEC", Modifier.weight(1f)) { out = Tools.b64d(input) }
        }
        NeonButton("GENERATE PASSWORD", Modifier.fillMaxWidth()) {
            out = Tools.genPass(input.trim().toIntOrNull() ?: 20)
        }
        if (out.isNotEmpty()) Panel {
            Mono(out, Ink, 12)
            Link("COPY") { clip.setText(AnnotatedString(out)) }
        }
    }
}

@Composable
private fun Bar(label: String, v: Int) {
    Mono("$label  $v%", Ink, 13, true)
    LinearProgressIndicator(
        progress = { v / 100f },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = Cyan, trackColor = PanelBg
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
fun HudScreen(back: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val bat = remember(tick) { Sys.battery() }
    val ram = remember(tick) { Sys.ram() }
    val disk = remember(tick) { Sys.disk() }
    val net = remember(tick) { Sys.net() }
    Box(Modifier.fillMaxSize()) {
        MatrixRain(Modifier.alpha(0.35f))
        Screen("System HUD", back) {
            Mono(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()), Cyan, 40, true)
            Mono(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date()), Dim)
            Spacer(Modifier.height(14.dp))
            Panel {
                Bar("BATTERY", bat)
                Bar("RAM", ram)
                Bar("STORAGE", disk)
                Mono("NETWORK: $net", Cyan, 14, true)
            }
        }
    }
}

@Composable
fun FocusScreen(back: () -> Unit) {
    var secs by remember { mutableStateOf(1500) }
    var run by remember { mutableStateOf(false) }
    LaunchedEffect(run) {
        while (run && secs > 0) { delay(1000); secs-- }
        if (run && secs == 0) {
            run = false
            Voice.say("Focus session complete, Captain. Take a short break.")
        }
    }
    Screen("Focus Timer", back) {
        Mono("%02d:%02d".format(secs / 60, secs % 60), Cyan, 64, true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton(if (run) "PAUSE" else "START", Modifier.weight(1f)) { run = !run }
            NeonButton("RESET", Modifier.weight(1f)) { run = false; secs = 1500 }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton("25 MIN", Modifier.weight(1f)) { run = false; secs = 1500 }
            NeonButton("50 MIN", Modifier.weight(1f)) { run = false; secs = 3000 }
        }
    }
}

@Composable
fun NetScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var out by remember { mutableStateOf("") }
    Screen("Network", back) {
        Mono("Connection: ${Sys.net()}", Cyan, 14, true)
        NeonButton("SCAN IP & LATENCY", Modifier.fillMaxWidth()) {
            scope.launch { out = "Scanning…"; out = Api.netInfo() }
        }
        if (out.isNotEmpty()) Panel { Mono(out, Ink, 13) }
    }
}

@Composable
fun SettingsScreen(back: () -> Unit, lock: () -> Unit) {
    var sel by remember { mutableStateOf(Store.get("aiPreset").ifBlank { "claude" }) }
    var url by remember { mutableStateOf(Store.get("aiUrl").ifBlank { Ai.preset(sel).url }) }
    var model by remember {
        mutableStateOf(Store.get("aiModel").ifBlank { Store.get("claudeModel").ifBlank { Ai.preset(sel).model } })
    }
    var aiKey by remember { mutableStateOf(Ai.key()) }
    var fb by remember { mutableStateOf(Store.get("fbToken")) }
    var newPin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var sure by remember { mutableStateOf(false) }
    Screen("Settings", back) {
        Mono("AI PROVIDER (voice brain + Doc Studio)", Cyan, 13, true)
        Mono("Pick any provider and paste its API key.", Dim, 11)
        Ai.presets.forEach { p ->
            Link((if (sel == p.id) "◉ " else "○ ") + p.label, if (sel == p.id) Cyan else Dim) {
                sel = p.id; url = p.url; model = p.model
            }
        }
        Field(aiKey, { aiKey = it }, "AI API key", secret = true)
        Field(model, { model = it }, "Model name")
        Field(url, { url = it }, "API URL")
        Spacer(Modifier.height(6.dp))
        Mono("OTHER KEYS", Cyan, 13, true)
        Field(fb, { fb = it }, "Meta access token", secret = true)
        Mono("Connect GitHub, Vercel, Netlify and Supabase in Accounts.", Dim, 11)
        NeonButton("SAVE", Modifier.fillMaxWidth()) {
            Store.put("aiPreset", sel)
            Store.put("aiType", Ai.preset(sel).type)
            Store.put("aiUrl", url.trim())
            Store.put("aiModel", model.trim())
            Store.put("aiKey", aiKey.trim())
            Store.put("fbToken", fb.trim())
            msg = "Saved securely."
        }
        Spacer(Modifier.height(10.dp))
        Field(newPin, { newPin = it.filter(Char::isDigit).take(8) }, "New access code (4-8 digits)", secret = true)
        NeonButton("CHANGE ACCESS CODE", Modifier.fillMaxWidth()) {
            if (newPin.length >= 4) { Store.put("pin", newPin); newPin = ""; msg = "Access code changed." }
            else msg = "Code must be at least 4 digits."
        }
        Spacer(Modifier.height(10.dp))
        NeonButton(if (sure) "TAP AGAIN TO ERASE EVERYTHING" else "🚨 PANIC WIPE", Modifier.fillMaxWidth()) {
            if (sure) { Store.wipe(); lock() } else { sure = true; msg = "This deletes all keys, notes and vault data." }
        }
        if (msg.isNotEmpty()) Mono(msg, Cyan)
    }
}
