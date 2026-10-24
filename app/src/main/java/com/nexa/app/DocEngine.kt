package com.nexa.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** One text line found by OCR. [orig] is what the scan read, [text] is the editable value. */
class Fld(val id: Int, val page: Int, val box: Rect, val orig: String, val color: Int, val added: Boolean = false) {
    var text by mutableStateOf(orig)
}

/** An image / logo / watermark placed over (or erasing) a region of a page. bmp == null means "erase area". */
class Slot(val page: Int, val box: Rect, val bmp: Bitmap?, val watermark: Boolean)

/** A scanned document: untouched page bitmaps + editable fields + image slots. */
class Tpl(val name: String, val pages: List<Bitmap>) {
    val fields = mutableStateListOf<Fld>()
    val slots = mutableStateListOf<Slot>()
    var font by mutableStateOf("sans")
}

object DocState {
    val files = mutableStateListOf<Pair<Uri, String>>()
    val templates = mutableStateListOf<Tpl>()
    var open by mutableStateOf<Tpl?>(null)
}

object DocEngine {
    const val SYS_EDIT =
        "You edit document templates. You receive a JSON list of text fields (id, text) and an instruction from the user. " +
        "Return ONLY a JSON array, no prose. Each item is either {\"id\":N,\"text\":\"new text\"} to change field N " +
        "(empty text erases it), or {\"after\":N,\"text\":\"new line\"} to add a new line of text directly below field N. " +
        "Include only fields that must change."

    const val SYS_FILL =
        "You fill document templates. You receive the template fields (id, text) and raw text extracted from a source file. " +
        "Decide which template fields hold sample values (names, dates, numbers, addresses, ids, amounts, titles) and replace them " +
        "with the matching information from the source file. Return ONLY a JSON array of {\"id\":N,\"text\":\"new value\"}. " +
        "Keep labels and unrelated text unchanged. If a field holds a label and value together, such as 'Name: John', " +
        "return the full new text, such as 'Name: Sara'."

    fun name(ctx: Context, uri: Uri): String =
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else "file"
            } ?: "file"
        } catch (e: Exception) { "file" }

    private fun downscale(b: Bitmap, max: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= max) return b
        val s = max.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    suspend fun loadBitmap(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { downscale(it, 2400) }
        } catch (e: Exception) { null }
    }

    /** PDF -> one bitmap per page (max 20 pages), image -> single bitmap. */
    suspend fun load(ctx: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val type = ctx.contentResolver.getType(uri) ?: ""
            if (type.contains("pdf")) {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList<Bitmap>()
                pfd.use {
                    PdfRenderer(it).use { rd ->
                        (0 until minOf(rd.pageCount, 20)).map { i ->
                            rd.openPage(i).use { pg ->
                                val w = 1600
                                val h = (w.toFloat() * pg.height / pg.width).toInt()
                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                b.eraseColor(Color.WHITE)
                                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b
                            }
                        }
                    }
                }
            } else {
                listOfNotNull(
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { downscale(it, 2400) }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ---------- colour sampling ----------
    private fun col(bmp: Bitmap, x: Int, y0: Int, y1: Int): Int {
        val xs = x.coerceIn(0, bmp.width - 1)
        val a = y0.coerceIn(0, bmp.height - 1)
        val b = y1.coerceIn(0, bmp.height - 1)
        val step = maxOf(1, (b - a) / 8)
        var r = 0; var g = 0; var bl = 0; var n = 0
        var y = a
        while (y <= b) {
            val p = bmp.getPixel(xs, y)
            r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++
            y += step
        }
        return if (n == 0) Color.WHITE else Color.rgb(r / n, g / n, bl / n)
    }

    fun edges(bmp: Bitmap, r: Rect): Pair<Int, Int> =
        col(bmp, r.left - 5, r.top, r.bottom) to col(bmp, r.right + 5, r.top, r.bottom)

    private fun ink(bmp: Bitmap, r: Rect, bg: Int): Int {
        var best = Color.BLACK
        var bd = -1
        val s = maxOf(1, minOf(r.width(), r.height()) / 16)
        var y = r.top.coerceAtLeast(0)
        while (y < minOf(r.bottom, bmp.height)) {
            var x = r.left.coerceAtLeast(0)
            while (x < minOf(r.right, bmp.width)) {
                val p = bmp.getPixel(x, y)
                val d = abs(Color.red(p) - Color.red(bg)) + abs(Color.green(p) - Color.green(bg)) + abs(Color.blue(p) - Color.blue(bg))
                if (d > bd) { bd = d; best = p }
                x += s
            }
            y += s
        }
        return best
    }

    // ---------- scanning ----------
    suspend fun scan(name: String, bms: List<Bitmap>): Tpl = withContext(Dispatchers.Default) {
        val t = Tpl(name, bms)
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var id = 0
        try {
            bms.forEachIndexed { pi, bmp ->
                val res = rec.process(InputImage.fromBitmap(bmp, 0)).await()
                for (b in res.textBlocks) for (l in b.lines) {
                    val bb = l.boundingBox ?: continue
                    if (l.text.isBlank()) continue
                    val r = Rect(bb)
                    val (cl, cr) = edges(bmp, r)
                    val bg = Color.rgb(
                        (Color.red(cl) + Color.red(cr)) / 2,
                        (Color.green(cl) + Color.green(cr)) / 2,
                        (Color.blue(cl) + Color.blue(cr)) / 2
                    )
                    t.fields.add(Fld(id++, pi, r, l.text, ink(bmp, r, bg)))
                }
            }
        } finally { rec.close() }
        t
    }

    suspend fun ocrText(bms: List<Bitmap>): String = withContext(Dispatchers.Default) {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        try {
            for (b in bms) sb.append(rec.process(InputImage.fromBitmap(b, 0)).await().text).append("\n")
        } finally { rec.close() }
        sb.toString()
    }

    // ---------- rendering ----------
    /** Original page pixels are kept 100%; only edited fields / slots are patched on top. */
    fun render(t: Tpl, pi: Int): Bitmap {
        val src = t.pages[pi]
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val tf = when (t.font) {
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }

        fun patch(r: Rect) {
            val (cl, cr) = edges(src, r)
            val l = (r.left - 3).coerceAtLeast(0).toFloat()
            val rr = (r.right + 3).coerceAtMost(out.width).toFloat()
            p.shader = LinearGradient(l, 0f, rr, 0f, cl, cr, Shader.TileMode.CLAMP)
            cv.drawRect(l, (r.top - 3).toFloat(), rr, (r.bottom + 3).toFloat(), p)
            p.shader = null
        }

        for (s in t.slots) if (s.page == pi) {
            if (!s.watermark) patch(s.box)
            val b = s.bmp ?: continue
            val sc = minOf(s.box.width().toFloat() / b.width, s.box.height().toFloat() / b.height)
            val w = b.width * sc
            val h = b.height * sc
            val l = s.box.exactCenterX() - w / 2
            val tp = s.box.exactCenterY() - h / 2
            p.alpha = if (s.watermark) 80 else 255
            cv.drawBitmap(b, null, RectF(l, tp, l + w, tp + h), p)
            p.alpha = 255
        }

        for (f in t.fields) if (f.page == pi && f.text != f.orig) {
            if (!f.added) patch(f.box)
            if (f.text.isNotBlank()) {
                p.color = f.color
                p.typeface = tf
                p.textSize = f.box.height() * 0.8f
                val maxW = (out.width - f.box.left - 8).toFloat()
                val w = p.measureText(f.text)
                if (w > maxW) p.textSize *= maxW / w
                val fm = p.fontMetrics
                cv.drawText(f.text, f.box.left.toFloat(), f.box.exactCenterY() - (fm.ascent + fm.descent) / 2, p)
            }
        }
        return out
    }

    // ---------- export ----------
    fun exportPdf(ctx: Context, t: Tpl, uri: Uri) {
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (i in t.pages.indices) {
            val b = render(t, i)
            val w = 595
            val h = (595f * b.height / b.width).toInt()
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            pg.canvas.drawBitmap(b, null, Rect(0, 0, w, h), paint)
            doc.finishPage(pg)
            b.recycle()
        }
        ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }

    fun exportPng(ctx: Context, t: Tpl, pi: Int, uri: Uri) {
        val b = render(t, pi)
        ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
    }

    // ---------- NEXA (Claude) edits ----------
    fun fieldsJson(t: Tpl): String {
        val a = JSONArray()
        t.fields.take(300).forEach { a.put(JSONObject().put("id", it.id).put("text", it.text)) }
        return "FIELDS: $a"
    }

    suspend fun claudeJson(system: String, user: String): String {
        val key = Store.get("claudeKey")
        if (key.isBlank()) return ""
        val body = JSONObject()
            .put("model", Store.get("claudeModel").ifBlank { "claude-sonnet-5-5" })
            .put("max_tokens", 4000)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val (c, b) = Net.call(
            "https://api.anthropic.com/v1/messages", "POST", body.toString(),
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
        )
        if (c != 200) return ""
        return try { JSONObject(b).getJSONArray("content").getJSONObject(0).getString("text") } catch (e: Exception) { "" }
    }

    /** Applies the JSON ops returned by Claude. Returns how many fields changed. */
    fun applyOps(t: Tpl, json: String): Int {
        val a = try {
            JSONArray(json.substring(json.indexOf('['), json.lastIndexOf(']') + 1))
        } catch (e: Exception) { return 0 }
        var n = 0
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val txt = o.optString("text", "")
            if (o.has("id")) {
                val f = t.fields.firstOrNull { it.id == o.optInt("id", -1) } ?: continue
                f.text = txt; n++
            } else if (o.has("after")) {
                val ref = t.fields.firstOrNull { it.id == o.optInt("after", -1) } ?: continue
                val h = ref.box.height()
                val nb = Rect(ref.box.left, ref.box.bottom + h / 3, ref.box.right, ref.box.bottom + h / 3 + h)
                val nid = (t.fields.maxOfOrNull { it.id } ?: 0) + 1
                val f = Fld(nid, ref.page, nb, "", ref.color, true)
                f.text = txt
                t.fields.add(f); n++
            }
        }
        return n
    }
}
