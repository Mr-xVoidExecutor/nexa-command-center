package com.nexa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(back: () -> Unit) {
    Screen("Account Connections", back) {
        Mono("CONNECT ONCE • STORE ENCRYPTED ON THIS DEVICE", Cyan, 13, true)
        Spacer(Modifier.height(5.dp))
        Mono(
            "Create an access token in each provider's official account dashboard, then connect it here. " +
                "NEXA never asks for or stores your Gmail or account password.",
            Dim,
            12
        )
        Spacer(Modifier.height(8.dp))

        ProviderAccountCard(
            provider = "github",
            name = "GitHub",
            description = "Repositories, private repo creation and GitHub Actions.",
            tokenKey = "ghToken",
            tokenLabel = "GitHub personal access token",
            tokenUrl = "https://github.com/settings/personal-access-tokens",
            permissions = "For repository tools, allow access to the repositories you need."
        )
        ProviderAccountCard(
            provider = "vercel",
            name = "Vercel",
            description = "Securely save a Vercel account access token for project and deployment tools.",
            tokenKey = "vercelToken",
            tokenLabel = "Vercel access token",
            tokenUrl = "https://vercel.com/account/settings/tokens",
            permissions = "Create a token in your Vercel account settings."
        )
        ProviderAccountCard(
            provider = "netlify",
            name = "Netlify",
            description = "Securely save a Netlify account access token for site and deployment tools.",
            tokenKey = "netlifyToken",
            tokenLabel = "Netlify personal access token",
            tokenUrl = "https://app.netlify.com/user/applications#personal-access-tokens",
            permissions = "Create a personal access token in your Netlify user settings."
        )
        ProviderAccountCard(
            provider = "supabase",
            name = "Supabase",
            description = "Project list and Supabase account management access.",
            tokenKey = "supabaseToken",
            tokenLabel = "Supabase personal access token",
            tokenUrl = "https://supabase.com/dashboard/account/tokens",
            permissions = "Create a personal access token in your Supabase account settings."
        )

        Panel {
            Mono("SUPABASE DATABASE PROJECT", Cyan, 13, true)
            Spacer(Modifier.height(4.dp))
            Mono(
                "The Supabase account token lists projects. Database queries also need a project URL " +
                    "and a publishable/anon API key. Do not put a service_role key in a mobile app.",
                Dim,
                11
            )
            var url by remember { mutableStateOf(Store.get("supaUrl")) }
            var key by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("") }
            Field(url, { url = it }, "Project URL (https://your-project.supabase.co)")
            Field(key, { key = it }, "Publishable / anon API key", secret = true)
            NeonButton("SAVE DATABASE CONNECTION", Modifier.fillMaxWidth()) {
                val cleanUrl = url.trim().trimEnd('/')
                if (!cleanUrl.matches(Regex("^https://[A-Za-z0-9.-]+(?::[0-9]+)?$"))) {
                    status = "Enter a valid HTTPS Supabase project URL."
                } else if (key.isBlank() && Store.get("supaKey").isBlank()) {
                    status = "Enter the project's publishable / anon API key."
                } else {
                    Store.put("supaUrl", cleanUrl)
                    if (key.isNotBlank()) Store.put("supaKey", key.trim())
                    key = ""
                    status = "Supabase database connection saved securely."
                }
            }
            if (status.isNotEmpty()) Mono(status, if (status.startsWith("Supabase")) Cyan else Pink, 11)
        }
    }
}

@Composable
private fun ProviderAccountCard(
    provider: String,
    name: String,
    description: String,
    tokenKey: String,
    tokenLabel: String,
    tokenUrl: String,
    permissions: String
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var draft by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(Store.get(tokenKey).isNotBlank()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (Store.get(tokenKey).isNotBlank()) "Token saved securely on this device." else "")
    }

    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono(name.uppercase(), Cyan, 15, true)
            Mono(if (connected) "● SAVED" else "○ NOT CONNECTED", if (connected) Cyan else Dim, 10, true)
        }
        Spacer(Modifier.height(3.dp))
        Mono(description, Dim, 11)
        Field(draft, { draft = it }, tokenLabel, secret = true)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            NeonButton(
                if (busy) "CHECKING…" else "CONNECT / SAVE",
                Modifier.weight(1f)
            ) {
                if (!busy) scope.launch {
                    busy = true
                    status = "Checking token with $name…"
                    val (ok, message) = Api.verifyAccount(provider, draft.trim())
                    if (ok) {
                        Store.put(tokenKey, draft.trim())
                        draft = ""
                        connected = true
                        status = message
                    } else {
                        status = message
                    }
                    busy = false
                }
            }
            if (connected) {
                Link("CHECK SAVED") {
                    if (!busy) scope.launch {
                        busy = true
                        status = "Checking saved $name connection…"
                        val (_, message) = Api.verifyAccount(provider, Store.get(tokenKey))
                        status = message
                        busy = false
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Link("OPEN OFFICIAL TOKEN PAGE") { uriHandler.openUri(tokenUrl) }
            if (connected) {
                Link("DISCONNECT", Pink) {
                    Store.put(tokenKey, "")
                    connected = false
                    status = "$name token removed from this device."
                }
            }
        }
        Mono(permissions, Dim, 10)
        if (status.isNotEmpty()) {
            val ok = status.contains("Connected to ") || status.contains("saved securely") ||
                status.contains("removed from this device")
            Mono(status, if (ok) Color(0xFF247A53) else Pink, 11)
        }
    }
}
