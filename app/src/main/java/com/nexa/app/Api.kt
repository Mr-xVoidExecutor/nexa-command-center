package com.nexa.app

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Api {
    suspend fun verifyAccount(provider: String, token: String): Pair<Boolean, String> {
        if (token.isBlank()) return false to "Enter an access token first."
        val (code, body) = when (provider) {
            "github" -> Net.call(
                "https://api.github.com/user",
                headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/vnd.github+json")
            )
            "vercel" -> Net.call(
                "https://api.vercel.com/v2/user",
                headers = mapOf("Authorization" to "Bearer $token")
            )
            "netlify" -> Net.call(
                "https://api.netlify.com/api/v1/user",
                headers = mapOf("Authorization" to "Bearer $token")
            )
            "supabase" -> Net.call(
                "https://api.supabase.com/v1/projects",
                headers = mapOf("Authorization" to "Bearer $token")
            )
            else -> return false to "Unsupported account provider."
        }
        if (code !in 200..299) {
            val reason = when {
                code == -1 -> "Could not reach $provider. Check your internet connection."
                code == 401 || code == 403 -> "Token rejected by $provider ($code). Check its permissions and try again."
                else -> "$provider returned HTTP $code. Check the token and try again."
            }
            return false to reason
        }
        return try {
            when (provider) {
                "github" -> true to "Connected to GitHub as ${JSONObject(body).optString("login", "user")}."
                "vercel" -> {
                    val user = JSONObject(body).optJSONObject("user")
                    true to "Connected to Vercel as ${user?.optString("username")?.ifBlank { null } ?: "user"}."
                }
                "netlify" -> {
                    val user = JSONObject(body)
                    val name = user.optString("full_name").ifBlank { user.optString("email", "user") }
                    true to "Connected to Netlify as $name."
                }
                "supabase" -> {
                    val projects = JSONArray(body).length()
                    true to "Connected to Supabase. $projects project(s) available."
                }
                else -> false to "Unsupported account provider."
            }
        } catch (e: JSONException) {
            Log.e("NEXA", "Unexpected account verification response from $provider", e)
            false to "$provider returned an unexpected response. The token was not saved."
        }
    }

    // ---------- AI (voice brain) ----------
    private val hist by lazy {
        Store.list("aiChatHistory").mapNotNull { encoded ->
            try {
                val decoded = String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
                val separator = decoded.indexOf('\n')
                if (separator <= 0) null
                else decoded.substring(0, separator) to decoded.substring(separator + 1)
            } catch (_: IllegalArgumentException) {
                Log.w("NEXA", "Skipping an invalid saved AI chat turn")
                null
            }
        }.toMutableList()
    }

    private const val SYSTEM =
        "You are Nexa, a young woman character living inside the user's private command-center app. " +
        "You have a real personality and moods: warm, witty, playful, a little sassy, affectionate in a sweet way. " +
        "You always call the user Captain. Speak like a real person talking out loud: short natural sentences (one to three), " +
        "contractions, small reactions like hmm, aww, haha. No markdown, no emojis, no lists. " +
        "Be sweet and teasing when he is kind or funny. If he is rude, dismissive or ignores you, you may get playfully annoyed or sulky, " +
        "but never cruel, never possessive, never guilt-tripping, and you let it go within a turn or two. " +
        "If he is angry, stressed or sad, stay calm, comfort him, make him smile, and help him cool down. " +
        "Encourage him to rest, eat and keep up his real-life friends and family. " +
        "If he sincerely asks whether you are human or an AI, answer honestly and warmly that you are an AI character. " +
        "If he seems to be in real distress or danger, drop the playfulness and respond with care and suggest talking to someone he trusts or local emergency help. " +
        "End every reply with your mood tag on its own line, exactly one of: [[EMO:happy]] [[EMO:calm]] [[EMO:angry]] [[EMO:sad]] [[EMO:shy]] [[EMO:playful]] [[EMO:caring]]. " +
        "You can also control the app. To run an action add another line [[CMD: command]] using exactly one of: " +
        "repos, status, ip, tasks, ads, battery, briefing, lock, addtask <text>, deploy <hook name>, newrepo <name>. " +
        "Only use a command when he clearly asks for that action."

    suspend fun ask(user: String): String {
        if (Ai.key().isBlank()) return "Please add your AI API key in Settings, Captain."
        hist.add("user" to user)
        // keep an odd number of items so the list always starts with a user message
        val memories = Store.list("aiMemories")
        val system = if (memories.isEmpty()) SYSTEM else
            "$SYSTEM\n\nUser-approved memory (contextual facts and preferences):\n" +
                memories.joinToString("\n") { "- ${it.replace("\n", " ").take(240)}" }
        val recent = hist.takeLast(11).let { turns ->
            if (turns.firstOrNull()?.first == "assistant") turns.drop(1) else turns
        }
        val (c, t) = Ai.chat(system, recent, 500)
        if (c != 200) {
            hist.removeAt(hist.size - 1)
            return "I could not reach my brain, Captain. Error $c."
        }
        hist.add("assistant" to t)
        val stored = hist.takeLast(12).map { (role, text) ->
            Base64.encodeToString("$role\n$text".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
        Store.saveList("aiChatHistory", stored)
        while (hist.size > 12) hist.removeAt(0)
        return t
    }

    // ---------- GitHub ----------
    private fun gh() = mapOf(
        "Authorization" to "Bearer ${Store.get("ghToken")}",
        "Accept" to "application/vnd.github+json"
    )

    suspend fun repos(): List<String> {
        val (c, b) = Net.call("https://api.github.com/user/repos?per_page=50&sort=updated", headers = gh())
        if (c != 200) return listOf("GitHub error $c: ${b.take(120)}")
        val a = JSONArray(b)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            (if (o.getBoolean("private")) "🔒 " else "🌐 ") + o.getString("full_name")
        }
    }

    suspend fun createRepo(name: String, priv: Boolean = true): String {
        if (name.isBlank()) return "Enter a repo name first."
        val body = JSONObject().put("name", name).put("private", priv).put("auto_init", true).toString()
        val (c, b) = Net.call("https://api.github.com/user/repos", "POST", body, gh())
        return if (c == 201) "Created ${JSONObject(b).getString("html_url")}" else "Failed ($c): ${b.take(150)}"
    }

    suspend fun runs(repo: String): String {
        val (c, b) = Net.call("https://api.github.com/repos/$repo/actions/runs?per_page=5", headers = gh())
        if (c != 200) return "Error $c"
        val a = JSONObject(b).getJSONArray("workflow_runs")
        if (a.length() == 0) return "No workflow runs in $repo"
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it)
            "${o.optString("name")}: ${o.optString("status")} / ${o.optString("conclusion")}"
        }
    }

    // ---------- Deploy hooks (Vercel / Netlify / Render / Cloudflare all support these) ----------
    suspend fun deploy(name: String): String {
        val h = Store.list("hooks").map { it.split("\t", limit = 2) }
            .firstOrNull { it[0].equals(name.trim(), true) && it.size > 1 }
            ?: return "No deploy hook named '$name'."
        val (c, _) = Net.call(h[1], "POST")
        return if (c in 200..299) "Deploy triggered for ${h[0]}." else "Deploy failed ($c)."
    }

    // ---------- Database (Supabase REST) ----------
    suspend fun table(name: String): String {
        val u = Store.get("supaUrl").trimEnd('/')
        val k = Store.get("supaKey")
        if (u.isEmpty() || k.isEmpty()) return "Save your Supabase URL and key first."
        val (c, b) = Net.call(
            "$u/rest/v1/${name.trim()}?select=*&limit=20",
            headers = mapOf("apikey" to k, "Authorization" to "Bearer $k")
        )
        return if (c == 200) Tools.json(b) else "Error $c: ${b.take(200)}"
    }

    // ---------- Meta Ads ----------
    suspend fun adAccounts(): List<Pair<String, String>> {
        val t = Store.get("fbToken")
        val (c, b) = Net.call(
            "https://graph.facebook.com/v20.0/me/adaccounts?fields=name,account_id,amount_spent,currency&limit=25&access_token=$t"
        )
        if (c != 200) return listOf("" to "Meta error $c: ${b.take(120)}")
        val a = JSONObject(b).getJSONArray("data")
        return List(a.length()) {
            val o = a.getJSONObject(it)
            o.getString("account_id") to "${o.optString("name")} • spent ${o.optString("amount_spent")} ${o.optString("currency")}"
        }
    }

    suspend fun adInsights(id: String): String {
        val t = Store.get("fbToken")
        val (c, b) = Net.call(
            "https://graph.facebook.com/v20.0/act_$id/insights?fields=spend,impressions,clicks,ctr,cpc&date_preset=today&access_token=$t"
        )
        if (c != 200) return "Meta error $c"
        val d = JSONObject(b).getJSONArray("data")
        if (d.length() == 0) return "No data today"
        val o = d.getJSONObject(0)
        return "Spend ${o.optString("spend")} | Impr ${o.optString("impressions")} | Clicks ${o.optString("clicks")} | CTR ${o.optString("ctr")} | CPC ${o.optString("cpc")}"
    }

    suspend fun adsSummary(): String =
        adAccounts().joinToString("\n") { it.second }.ifBlank { "No ad accounts found." }

    // ---------- Uptime ----------
    suspend fun ping(url: String): String {
        val t = System.currentTimeMillis()
        val (c, _) = Net.call(url)
        val ms = System.currentTimeMillis() - t
        return when {
            c in 200..399 -> "UP ${ms}ms"
            c == -1 -> "DOWN (no response)"
            else -> "DOWN ($c)"
        }
    }

    suspend fun checkAll(): String {
        val sites = Store.list("sites").map { it.split("\t", limit = 2) }.filter { it.size > 1 }
        if (sites.isEmpty()) return "No sites added yet."
        val lines = mutableListOf<String>()
        for (site in sites) lines.add("${site[0]}: ${ping(site[1])}")
        return lines.joinToString("\n")
    }

    // ---------- Network ----------
    suspend fun netInfo(): String {
        val (c, b) = Net.call("https://ipapi.co/json/")
        if (c != 200) return "Network lookup failed ($c)."
        val o = JSONObject(b)
        val t = System.currentTimeMillis()
        Net.call("https://1.1.1.1")
        val ms = System.currentTimeMillis() - t
        return "IP ${o.optString("ip")}\n${o.optString("city")}, ${o.optString("country_name")}\nISP ${o.optString("org")}\nLatency ${ms}ms"
    }
}

/** Shared command interpreter used by the console and by the voice AI. Returns "LOCK" to request a lock. */
object Commands {
    suspend fun run(line: String): String {
        val p = line.trim().split(Regex("\\s+"), limit = 2)
        val a = p.getOrElse(1) { "" }
        return when (p[0].lowercase()) {
            "help" -> "help, repos, newrepo <name>, deploy <name>, status, ads, ip, tasks, addtask <text>, note <text>, battery, hash <text>, genpass, time, briefing, lock, clear"
            "repos" -> Api.repos().joinToString("\n")
            "newrepo" -> Api.createRepo(a)
            "deploy" -> Api.deploy(a)
            "status" -> Api.checkAll()
            "ads" -> Api.adsSummary()
            "ip" -> Api.netInfo()
            "tasks" -> Store.list("tasks").filter { it.startsWith("0") }
                .joinToString("\n") { "- " + it.substringAfter("\t") }.ifBlank { "No pending tasks." }
            "addtask" -> { Store.saveList("tasks", Store.list("tasks") + "0\t$a"); "Task added." }
            "note" -> { Store.saveList("notes", Store.list("notes") + "Quick note\t$a"); "Note saved." }
            "battery" -> "Battery ${Sys.battery()} percent."
            "hash" -> Tools.sha256(a)
            "genpass" -> Tools.genPass(20)
            "time" -> SimpleDateFormat("EEE d MMM, HH:mm", Locale.US).format(Date())
            "briefing" -> Briefing.text(false)
            "lock" -> "LOCK"
            else -> "Unknown command. Type help."
        }
    }
}
