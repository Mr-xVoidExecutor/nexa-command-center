package com.nexa.app

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    val lockSignal = mutableStateOf(0)
    private var stopAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Voice.init(applicationContext)
        setContent {
            NexaTheme {
                // UI runs at 125% scale
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density * 1.25f, d.fontScale)) {
                    Root(this@MainActivity)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Auto-lock if the app was in the background for more than 60 seconds
        if (stopAt > 0 && System.currentTimeMillis() - stopAt > 60_000) lockSignal.value++
    }
}

@Composable
fun Root(act: MainActivity) {
    var screen by remember { mutableStateOf("login") }
    LaunchedEffect(act.lockSignal.value) {
        if (act.lockSignal.value > 0) { Voice.stop(); screen = "login" }
    }
    BackHandler(enabled = screen != "login" && screen != "home") { screen = "home" }
    val back = { screen = "home" }
    val lock = { Voice.stop(); screen = "login" }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (screen) {
            "login" -> LoginScreen(act) { screen = "home" }
            "home" -> HomeScreen({ screen = it }, lock)
            "ai" -> AiScreen(back, lock)
            "docs" -> DocsScreen(back)
            "github" -> GithubScreen(back)
            "deploy" -> DeployScreen(back)
            "db" -> DbScreen(back)
            "ads" -> AdsScreen(back)
            "tasks" -> TasksScreen(back)
            "notes" -> NotesScreen(back)
            "vault" -> VaultScreen(back)
            "uptime" -> UptimeScreen(back)
            "console" -> ConsoleScreen(back, lock)
            "tools" -> ToolsScreen(back)
            "hud" -> HudScreen(back)
            "focus" -> FocusScreen(back)
            "net" -> NetScreen(back)
            "settings" -> SettingsScreen(back, lock)
        }
    }
}
