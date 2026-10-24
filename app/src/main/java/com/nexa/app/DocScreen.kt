package com.nexa.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun DocsScreen(back: () -> Unit) {
    val t = DocState.open
    BackHandler(enabled = t != null) { DocState.open = null }
    if (t == null) UploadScreen(back) else EditorScreen(t) { DocState.open = null }
}

@Composable
fun UploadScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> DocState.files.add(u to DocEngine.name(ctx, u)) }
    }
    Screen("Doc Studio", back) {
        Mono("Upload as many PDFs / images as you like. NEXA scans each one into an editable template that keeps the original layout.", Dim, 12)
        Spacer(Modifier.height(6.dp))
        NeonButton("＋ ADD FILES (PDF / IMAGE)", Modifier.fillMaxWidth()) {
            pick.launch(arrayOf("application/pdf", "image/*"))
        }
        DocState.files.forEachIndexed { i, (u, n) ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono("File ${i + 1}", Cyan, 14, true)
                    Mono("  $n", Dim, 11, modifier = Modifier.weight(1f))
                    Link("✕", Pink) { DocState.files.removeAt(i) }
                }
            }
        }
        if (DocState.files.isNotEmpty()) {
            NeonButton(if (busy.isEmpty()) "🔍 SCAN & BUILD TEMPLATES" else busy, Modifier.fillMaxWidth()) {
                if (busy.isEmpty()) scope.launch {
                    err = ""
                    DocState.templates.clear()
                    DocState.files.forEachIndexed { i, (u, n) ->
                        busy = "Scanning file ${i + 1}…"
                        try {
                            val bms = DocEngine.load(ctx, u)
                            if (bms.isNotEmpty()) DocState.templates.add(DocEngine.scan(n, bms))
                            else err = "Could not open file ${i + 1}."
                        } catch (e: Exception) { err = "Scan failed for file ${i + 1}: ${e.message}" }
                    }
                    busy = ""
                    Voice.say("Scan complete. ${DocState.templates.size} templates are ready.", "happy")
                }
            }
        }
        if (err.isNotEmpty()) Mono(err, Pink, 12)
        DocState.templates.forEachIndexed { i, t ->
            Panel(Modifier.clickable { DocState.open = t }) {
                Mono("Template ${i + 1}", Cyan, 15, true)
                Mono("${t.name} • ${t.pages.size} page(s) • ${t.fields.size} fields", Dim, 11)
                Mono("tap to open the form & editor", Dim, 11)
            }
        }
        Spacer(Modifier.height(10.dp))
        Mono("Text recognition covers Latin-script text (English etc.). Use only documents you own or are allowed to edit.", Dim, 10)
    }
}

@Composable
private fun FieldRow(f: Fld, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(f.text, { f.text = it; onChange() }, f.orig.take(40).ifBlank { "(new line)" }, modifier = Modifier.weight(1f))
        Link("✕", Pink) { f.text = ""; onChange() }
    }
}

@Composable
fun EditorScreen(t: Tpl, close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pi by remember { mutableStateOf(0) }
    var ver by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Rect?>(null) }
    var cmd by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var vw by remember { mutableStateOf(1) }
    var a by remember { mutableStateOf(Offset.Zero) }
    var b by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(ver, pi, t.font, t.slots.size) {
        delay(300)
        preview = withContext(Dispatchers.Default) { DocEngine.render(t, pi) }
    }

    fun reply(n: Int, ok: String, fail: String) {
        ver++
        status = if (n > 0) ok else fail
        Voice.say(if (n > 0) "Done, Captain. $ok" else "Hmm, I couldn't find anything to change.", if (n > 0) "happy" else "calm")
    }

    fun runCmd(text: String) {
        scope.launch {
            status = "NEXA is editing…"
            val out = DocEngine.claudeJson(DocEngine.SYS_EDIT, DocEngine.fieldsJson(t) + "\nINSTRUCTION: " + text)
            val n = DocEngine.applyOps(t, out)
            reply(n, "Updated $n field(s).", "No change. Check your Claude key in Settings or rephrase.")
        }
    }

    fun autofill(uri: Uri) {
        scope.launch {
            status = "Reading data file…"
            val type = ctx.contentResolver.getType(uri) ?: ""
            val src = if (type.startsWith("text")) withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            } else DocEngine.ocrText(DocEngine.load(ctx, uri))
            status = "NEXA is mapping the data…"
            val out = DocEngine.claudeJson(DocEngine.SYS_FILL, DocEngine.fieldsJson(t) + "\nSOURCE:\n" + src.take(6000))
            val n = DocEngine.applyOps(t, out)
            reply(n, "Filled $n field(s) from your file.", "Nothing was filled. Check your Claude key in Settings.")
        }
    }

    val imgPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val r = pending
        if (uri != null && r != null) scope.launch {
            val bm = DocEngine.loadBitmap(ctx, uri)
            if (bm != null) { t.slots.add(Slot(pi, r, bm, mode == "watermark")); ver++ }
            pending = null; mode = ""
        } else { pending = null; mode = "" }
    }
    val dataPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) autofill(u) }
    val pdfOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        if (u != null) scope.launch {
            status = "Exporting PDF…"
            withContext(Dispatchers.IO) { DocEngine.exportPdf(ctx, t, u) }
            status = "PDF saved."
        }
    }
    val pngOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { u ->
        if (u != null) scope.launch {
            status = "Exporting image…"
            withContext(Dispatchers.IO) { DocEngine.exportPng(ctx, t, pi, u) }
            status = "Image saved."
        }
    }

    val listener = remember { Listener(ctx, { cmd = it; runCmd(it) }, { listening = it }) }
    DisposableEffect(Unit) { onDispose { listener.destroy() } }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listener.start() }
    fun mic() {
        Voice.stop()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listener.start()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun finishDrag(bmp: Bitmap) {
        val s = bmp.width.toFloat() / vw
        val r = Rect((min(a.x, b.x) * s).toInt(), (min(a.y, b.y) * s).toInt(), (max(a.x, b.x) * s).toInt(), (max(a.y, b.y) * s).toInt())
        if (r.width() < 12 || r.height() < 12) return
        if (mode == "erase") { t.slots.add(Slot(pi, r, null, false)); ver++; mode = "" }
        else { pending = r; imgPick.launch("image/*") }
    }

    Screen("Template • ${t.name.take(22)}", close) {
        // 1. data file
        Panel {
            Mono("1 · FILL FROM A DATA FILE", Cyan, 13, true)
            Mono("Upload a PDF / image / text file. NEXA reads it and puts each detail in the right place.", Dim, 11)
            NeonButton("📎 UPLOAD DATA FILE", Modifier.fillMaxWidth()) { dataPick.launch(arrayOf("application/pdf", "image/*", "text/plain")) }
        }
        // 2. NEXA
        Panel {
            Mono("2 · TELL NEXA WHAT TO CHANGE", Cyan, 13, true)
            Field(cmd, { cmd = it }, "e.g. change the name to Sara Khan, add a line below the date")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NeonButton(if (listening) "● LISTENING" else "🎙 SPEAK", Modifier.weight(1f)) { mic() }
                NeonButton("SEND", Modifier.weight(1f)) { if (cmd.isNotBlank()) runCmd(cmd) }
            }
        }
        if (status.isNotEmpty()) Mono(status, Pink, 12)

        // page switcher + preview
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Link("◀") { if (pi > 0) pi-- }
            Mono("Page ${pi + 1} / ${t.pages.size}", Color.White, 13, true)
            Link("▶") { if (pi < t.pages.size - 1) pi++ }
        }
        val bmp = preview
        if (bmp == null) Mono("Rendering…", Dim) else {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Box(
                Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)
                    .onSizeChanged { vw = max(1, it.width) }
                    .border(1.dp, Cyan)
                    .pointerInput(mode) {
                        if (mode.isNotEmpty()) detectDragGestures(
                            onDragStart = { a = it; b = it; dragging = true },
                            onDragEnd = { dragging = false; finishDrag(bmp) },
                            onDragCancel = { dragging = false }
                        ) { change, _ -> b = change.position }
                    }
            ) {
                Image(img, null, Modifier.fillMaxSize())
                if (dragging) Canvas(Modifier.fillMaxSize()) {
                    drawRect(Cyan, Offset(min(a.x, b.x), min(a.y, b.y)), Size(abs(a.x - b.x), abs(a.y - b.y)), style = Stroke(4f))
                }
            }
        }

        // 3. images / watermark tools
        Panel {
            Mono("3 · LOGOS, PHOTOS & WATERMARKS", Cyan, 13, true)
            if (mode.isNotEmpty()) Mono("Drag on the page to select the area…", Pink, 12)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NeonButton("🖼 REPLACE", Modifier.weight(1f)) { mode = "replace" }
                NeonButton("💧 MARK", Modifier.weight(1f)) { mode = "watermark" }
                NeonButton("🧽 ERASE", Modifier.weight(1f)) { mode = "erase" }
            }
            if (mode.isNotEmpty()) Link("CANCEL", Pink) { mode = "" }
            t.slots.filter { it.page == pi }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono(if (s.watermark) "💧 Watermark" else if (s.bmp == null) "🧽 Erased area" else "🖼 Image slot", Color.White, 12, modifier = Modifier.weight(1f))
                    Link("✕", Pink) { t.slots.remove(s); ver++ }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Mono("Font:", Dim, 12)
                listOf("sans" to "SANS", "serif" to "SERIF", "mono" to "MONO").forEach { (k, l) ->
                    Link(l, if (t.font == k) Cyan else Dim) { t.font = k }
                }
            }
        }

        // 4. generated form
        Mono("4 · FORM (every text line of this page)", Cyan, 13, true)
        t.fields.filter { it.page == pi }.forEach { f -> FieldRow(f) { ver++ } }

        // 5. export
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton("⬇ PDF", Modifier.weight(1f)) { pdfOut.launch("nexa_${t.name.substringBeforeLast('.')}.pdf") }
            NeonButton("⬇ IMAGE", Modifier.weight(1f)) { pngOut.launch("nexa_${t.name.substringBeforeLast('.')}_p${pi + 1}.png") }
        }
        Spacer(Modifier.height(30.dp))
    }
}
