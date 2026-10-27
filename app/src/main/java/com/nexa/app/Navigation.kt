package com.nexa.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

val LocalDrawerOpen = compositionLocalOf { false }
val LocalToggleDrawer = compositionLocalOf<() -> Unit> { {} }

private data class ModuleDestination(
    val id: String,
    val label: String,
    val section: String,
    val icon: ImageVector
)

private val moduleDestinations = listOf(
    ModuleDestination("ai", "AI Voice", "AI & Voice", Icons.Filled.Mic),
    ModuleDestination("github", "GitHub", "Dev & Cloud", Icons.Filled.Code),
    ModuleDestination("deploy", "Deploy", "Dev & Cloud", Icons.Filled.RocketLaunch),
    ModuleDestination("db", "Database", "Dev & Cloud", Icons.Filled.Storage),
    ModuleDestination("console", "Console", "Dev & Cloud", Icons.Filled.Code),
    ModuleDestination("ads", "Meta Ads", "Marketing", Icons.Filled.Campaign),
    ModuleDestination("tasks", "Tasks", "Productivity", Icons.Filled.Checklist),
    ModuleDestination("notes", "Notes", "Productivity", Icons.AutoMirrored.Filled.Notes),
    ModuleDestination("focus", "Focus Timer", "Productivity", Icons.Filled.Timer),
    ModuleDestination("vault", "Vault", "Security", Icons.Filled.Security),
    ModuleDestination("docs", "Doc Studio", "Tools", Icons.Filled.Description),
    ModuleDestination("tools", "Toolbox", "Tools", Icons.Filled.Build),
    ModuleDestination("net", "Network", "Tools", Icons.Filled.Public),
    ModuleDestination("uptime", "Uptime", "System", Icons.Filled.MonitorHeart),
    ModuleDestination("hud", "System HUD", "System", Icons.Filled.Speed),
    ModuleDestination("settings", "Settings", "System", Icons.Filled.Settings)
)

@Composable
fun DrawerToggle(modifier: Modifier = Modifier) {
    val open = LocalDrawerOpen.current
    val toggle = LocalToggleDrawer.current
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            toggle()
        },
        modifier = modifier.size(44.dp)
    ) {
        AnimatedContent(
            targetState = open,
            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
            label = "drawer-toggle"
        ) { isOpen ->
            Icon(
                imageVector = if (isOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu,
                contentDescription = if (isOpen) "Close navigation" else "Open navigation",
                tint = Cyan
            )
        }
    }
}

@Composable
fun NavigationDrawer(
    selected: String,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val filtered = remember(query) {
        moduleDestinations.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Column(
        modifier
            .width(292.dp)
            .fillMaxHeight()
            .background(PanelBg)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(Voice.speaking.value, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Mono("NEXA", Cyan, 18, true)
                Mono("● SYSTEM ONLINE", Dim, 10, true)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close navigation", tint = Dim)
            }
        }
        Spacer(Modifier.height(18.dp))
        DrawerDestination(
            label = "Command Center",
            icon = Icons.Filled.Home,
            selected = selected == "home",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigate("home")
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Bg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Dim, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(start = 9.dp).weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                decorationBox = { inner ->
                    if (query.isEmpty()) Mono("Search modules", Dim, 12)
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Dim)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val sections = listOf("AI & Voice", "Dev & Cloud", "Marketing", "Productivity", "Security", "Tools", "System")
            sections.forEach { section ->
                val sectionDestinations = filtered.filter { it.section == section }
                if (sectionDestinations.isNotEmpty()) {
                    item(key = "header-$section") { SectionHeader(section) }
                    items(sectionDestinations, key = { it.id }) { destination ->
                        DrawerDestination(
                            label = destination.label,
                            icon = destination.icon,
                            selected = selected == destination.id,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigate(destination.id)
                            }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Mono("No modules match \"$query\"", Dim, 12, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Mono(label.uppercase(), Dim, 10, true, Modifier.padding(start = 12.dp, top = 14.dp, bottom = 5.dp))
}

@Composable
private fun DrawerDestination(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Cyan.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (selected) Modifier.background(Cyan.copy(alpha = 0.04f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Cyan)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Cyan else Dim,
            modifier = Modifier.padding(start = if (selected) 9.dp else 0.dp).size(19.dp)
        )
        Mono(label, if (selected) Cyan else Ink, 13, selected, Modifier.padding(start = 12.dp))
    }
}

@Composable
fun DrawerLayer(
    selected: String,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        NavigationDrawer(selected, onNavigate, onClose)
        Spacer(
            Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClose)
        )
    }
}
