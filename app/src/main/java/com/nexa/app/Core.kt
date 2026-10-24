package com.nexa.app

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Encrypted storage (Android Keystore backed). Lists are stored as JSON arrays of "a<TAB>b" strings. */
object Store {
    lateinit var ctx: Context
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        ctx = c
        val key = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        sp = EncryptedSharedPreferences.create(
            c, "nexa_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun get(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun put(k: String, v: String) { sp.edit().putString(k, v).apply() }
    fun list(k: String): List<String> {
        val a = JSONArray(get(k, "[]"))
        return List(a.length()) { a.getString(it) }
    }
    fun saveList(k: String, l: List<String>) = put(k, JSONArray(l).toString())
    fun wipe() { sp.edit().clear().apply() }
}

object Net {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Returns (httpCode, body). Code -1 means network failure. */
    suspend fun call(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val rb = if (method == "GET") null else (body ?: "").toRequestBody("application/json".toMediaType())
            val b = Request.Builder().url(url).method(method, rb)
            headers.forEach { (k, v) -> b.header(k, v) }
            http.newCall(b.build()).execute().use { Pair(it.code, it.body?.string() ?: "") }
        } catch (e: Exception) {
            Pair(-1, e.message ?: "network error")
        }
    }
}

object Sys {
    private val c get() = Store.ctx

    fun battery(): Int =
        (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun ram(): Int {
        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return (100 - mi.availMem * 100 / mi.totalMem).toInt()
    }

    fun disk(): Int {
        val s = StatFs(Environment.getDataDirectory().path)
        return (100 - s.availableBytes * 100 / s.totalBytes).toInt()
    }

    fun net(): String {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps == null -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE DATA"
            else -> "ONLINE"
        }
    }
}

object Tools {
    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun b64e(s: String): String = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    fun b64d(s: String): String =
        try { String(Base64.decode(s.trim(), Base64.DEFAULT)) } catch (e: Exception) { "Invalid Base64" }

    fun json(s: String): String =
        try {
            if (s.trim().startsWith("[")) JSONArray(s).toString(2) else JSONObject(s).toString(2)
        } catch (e: Exception) { "Invalid JSON" }

    fun genPass(n: Int): String {
        val cs = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%^&*"
        val r = SecureRandom()
        return (1..n.coerceIn(8, 64)).map { cs[r.nextInt(cs.length)] }.joinToString("")
    }
}

object Briefing {
    fun text(granted: Boolean): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val g = when {
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        val pending = Store.list("tasks").count { it.startsWith("0") }
        val t = if (pending == 1) "task" else "tasks"
        val head = if (granted) "$g, Captain. Access granted." else "$g, Captain."
        return "$head You have $pending pending $t. Battery is at ${Sys.battery()} percent. All systems are ready."
    }
}
