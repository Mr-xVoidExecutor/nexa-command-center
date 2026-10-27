package com.nexa.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(act: FragmentActivity, onOk: () -> Unit) {
    var lines by remember { mutableStateOf(listOf<String>()) }
    var booted by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val hasPin = Store.get("pin").isNotEmpty()
    val bioOk = BiometricManager.from(act)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    LaunchedEffect(Unit) {
        val boot = listOf(
            "> BOOTING NEXA CORE v1.0", "> LOADING NEURAL VOICE ENGINE", "> MOUNTING ENCRYPTED VAULT",
            "> LINKING GITHUB / DEPLOY / DATABASE", "> ALL SYSTEMS ONLINE"
        )
        for (l in boot) { lines = lines + l; delay(450) }
        booted = true
        delay(300)
        Voice.say(
            if (hasPin) "Hello Captain. Systems online. Please verify your identity."
            else "Welcome, Captain. I am Nexa. Please create your access code."
        )
    }

    fun granted() { Voice.say(Briefing.text(true)); onOk() }

    fun check() {
        if (!hasPin) {
            if (pin.length >= 4) { Store.put("pin", pin); granted() } else msg = "PIN must be at least 4 digits"
        } else if (pin == Store.get("pin")) {
            granted()
        } else {
            msg = "ACCESS DENIED"; pin = ""; Voice.say("Access denied.")
        }
    }

    fun bio() {
        val prompt = BiometricPrompt(
            act, ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { granted() }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("NEXA ACCESS").setSubtitle("Verify identity")
                .setNegativeButtonText("Use PIN").build()
        )
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        MatrixRain(Modifier.alpha(0.5f))
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!booted) {
                lines.forEach { Mono(it, Cyan) }
            } else {
                Avatar(Voice.speaking.value, Modifier.fillMaxWidth().height(370.dp))
                Spacer(Modifier.height(10.dp))
                Mono("N E X A", Cyan, 28, true)
                Mono(if (hasPin) "ENTER ACCESS CODE" else "CREATE ACCESS CODE (4-8 digits)", Dim)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink, unfocusedTextColor = Ink,
                        focusedContainerColor = PanelBg, unfocusedContainerColor = PanelBg,
                        focusedBorderColor = Cyan, unfocusedBorderColor = Purple,
                        focusedLabelColor = Cyan, unfocusedLabelColor = Dim, cursorColor = Cyan
                    )
                )
                if (msg.isNotEmpty()) Mono(msg, Pink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonButton(if (hasPin) "UNLOCK" else "CREATE") { check() }
                    if (hasPin && bioOk) NeonButton("👆 BIO") { bio() }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(go: (String) -> Unit, lock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawerToggle()
            Column(Modifier.weight(1f)) {
                Mono("NEXA COMMAND CENTER", Cyan, 17, true)
                Mono("PERSONAL SYSTEMS • ONLINE", Dim, 10, true)
            }
            Link("LOCK", Pink) { lock() }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Avatar(Voice.speaking.value, Modifier.fillMaxWidth().height(440.dp))
            Spacer(Modifier.height(14.dp))
            Panel {
                Mono("SYSTEMS NOMINAL", Cyan, 15, true)
                Spacer(Modifier.height(4.dp))
                Mono("Your command center is ready, Captain.", Ink, 13)
            }
            Spacer(Modifier.height(8.dp))
            NeonButton("START A CONVERSATION", Modifier.fillMaxWidth()) { go("ai") }
        }
    }
}

@Composable
fun AiScreen(back: () -> Unit, lock: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf("Tap the mic and talk to me, Captain.") }
    var heard by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var memoryDraft by remember { mutableStateOf("") }
    val memories = remember { mutableStateListOf<String>().apply { addAll(Store.list("aiMemories")) } }

    fun handle(text: String) {
        heard = text
        val lower = text.lowercase()
        val motion = when {
            "dance" in lower -> "dance"
            "run" in lower -> "run"
            "walk" in lower -> "walk"
            "sit" in lower -> "sit"
            "wave" in lower -> "wave"
            else -> null
        }
        val shortDirectMotion = motion != null && lower.split(Regex("\\s+")).size <= 3 &&
            (lower.startsWith(motion) || lower.startsWith("please $motion"))
        if (motion != null && ("nexa" in lower || "avatar" in lower || "you " in lower || shortDirectMotion)) {
            AvatarMotion.pose.value = motion
            Mood.now.value = if (motion == "dance" || motion == "wave") "playful" else "happy"
            reply = when (motion) {
                "dance" -> "All right, Captain. One tiny victory dance."
                "run" -> "Running a quick lap, Captain."
                "walk" -> "Taking a little walk, Captain."
                "sit" -> "Taking a seat. What is next?"
                else -> "Hi, Captain!"
            }
            Voice.say(reply, Mood.now.value)
            return
        }
        scope.launch {
            busy = true
            when {
                listOf("sad", "upset", "anxious", "worried", "lonely", "stress").any { it in lower } ->
                    Mood.now.value = "caring"
                listOf("happy", "great", "excited", "awesome", "love this").any { it in lower } ->
                    Mood.now.value = "happy"
            }
            val r = Api.ask(text)
            val re = Regex("\\[\\[CMD:(.*?)]]")
            val emoRe = Regex("\\[\\[EMO:(\\w+)]]")
            val emo = emoRe.find(r)?.groupValues?.get(1) ?: "calm"
            Mood.now.value = emo
            var spoken = r.replace(re, "").replace(emoRe, "").trim()
            val m = re.find(r)
            if (m != null) {
                val out = Commands.run(m.groupValues[1].trim())
                if (out == "LOCK") { busy = false; lock(); return@launch }
                spoken = (spoken + " " + out.lines().take(4).joinToString(". ")).trim()
            }
            reply = spoken
            busy = false
            Voice.say(spoken, emo)
        }
    }

    val listener = remember { Listener(ctx, { handle(it) }, { listening = it }, { heard = it }) }
    DisposableEffect(Unit) { onDispose { listener.destroy() } }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listener.start()
    }

    fun mic() {
        Voice.stop()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            listener.start()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }

    Screen("AI Voice", back) {
        Avatar(Voice.speaking.value, Modifier.fillMaxWidth().height(430.dp))
        AvatarMotionControls(Modifier.height(36.dp))
        Spacer(Modifier.height(8.dp))
        Panel {
            Mono("YOU: $heard", Dim)
            Spacer(Modifier.height(6.dp))
            Mono(if (busy) "thinking…" else reply, Cyan, 14)
        }
        if (memories.isNotEmpty()) {
            Panel {
                Mono("NEXA REMEMBERS", Cyan, 12, true)
                memories.forEachIndexed { index, memory ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(memory, Ink, 12, modifier = Modifier.weight(1f))
                        Link("REMOVE", Pink) {
                            memories.removeAt(index)
                            Store.saveList("aiMemories", memories.toList())
                        }
                    }
                }
            }
        }
        Field(memoryDraft, { memoryDraft = it }, "A preference for NEXA to remember")
        NeonButton("SAVE PRIVATE MEMORY", Modifier.fillMaxWidth()) {
            val memory = memoryDraft.trim()
            if (memory.isNotEmpty() && memory !in memories) {
                memories.add(memory)
                Store.saveList("aiMemories", memories.toList())
                memoryDraft = ""
            }
        }
        Mono("Stored encrypted on this device; shared with your selected AI provider when chatting.", Dim, 10)
        NeonButton(if (listening) "● LISTENING…" else "🎙 TALK TO NEXA", Modifier.fillMaxWidth()) { mic() }
        Field(typed, { typed = it }, "Or type a message")
        NeonButton("SEND", Modifier.fillMaxWidth()) {
            if (typed.isNotBlank()) { val t = typed; typed = ""; handle(t) }
        }
        Mono("Try: “check my sites”, “add task buy domain”, “NEXA, dance”, “sit”, “what's my battery”, “lock”", Dim, 11)
    }
}
