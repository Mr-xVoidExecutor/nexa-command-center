package com.nexa.app

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    var drawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(act.lockSignal.value) {
        if (act.lockSignal.value > 0) {
            Voice.stop()
            drawerOpen = false
            screen = "login"
        }
    }
    BackHandler(enabled = drawerOpen || (screen != "login" && screen != "home")) {
        if (drawerOpen) drawerOpen = false else screen = "home"
    }
    val back = { screen = "home" }
    val lock = { Voice.stop(); screen = "login" }
    val toggleDrawer = { drawerOpen = !drawerOpen }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .pointerInput(screen, drawerOpen) {
                var startX = 0f
                var horizontalDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        startX = it.x
                        horizontalDistance = 0f
                    },
                    onHorizontalDrag = { _, amount ->
                        horizontalDistance += amount
                        if (!drawerOpen && startX <= 36.dp.toPx() && horizontalDistance > 72.dp.toPx()) {
                            drawerOpen = true
                        } else if (drawerOpen && horizontalDistance < -72.dp.toPx()) {
                            drawerOpen = false
                        }
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { it / 7 }
                ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(280))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetOffsetX = { -it / 12 }
                    ) + fadeOut(tween(180)))
            },
            label = "screen-transition"
        ) { target ->
            CompositionLocalProvider(
                LocalDrawerOpen provides drawerOpen,
                LocalToggleDrawer provides toggleDrawer
            ) {
                when (target) {
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
        AnimatedVisibility(
            visible = drawerOpen && screen != "login",
            enter = fadeIn(tween(120)) + slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(tween(100)) + slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(
                LocalDrawerOpen provides true,
                LocalToggleDrawer provides toggleDrawer
            ) {
                DrawerLayer(
                    selected = screen,
                    onNavigate = {
                        screen = it
                        drawerOpen = false
                    },
                    onClose = { drawerOpen = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
