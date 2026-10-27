package com.nexa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun PermissionRegistryScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(RegistryBackend.signedIn()) }
    var email by remember { mutableStateOf(RegistryBackend.email()) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var clients by remember { mutableStateOf(emptyList<RegisteredClient>()) }
    var enrollmentCode by remember { mutableStateOf("") }
    var codeExpirySeconds by remember { mutableStateOf(0) }

    suspend fun refreshClients() {
        try {
            clients = RegistryBackend.loadClients()
            status = "Updated just now."
        } catch (e: Exception) {
            status = e.message ?: "Could not load the permission registry."
        }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        try {
            if (!RegistryBackend.restoreSession()) {
                signedIn = false
                return@LaunchedEffect
            }
        } catch (e: Exception) {
            status = e.message ?: "Could not restore the Supabase session."
            signedIn = false
            return@LaunchedEffect
        }
        while (signedIn) {
            refreshClients()
            delay(5_000)
            if (codeExpirySeconds > 0) {
                codeExpirySeconds = (codeExpirySeconds - 5).coerceAtLeast(0)
                if (codeExpirySeconds == 0) enrollmentCode = ""
            }
        }
    }

    Screen("Permission Registry", back) {
        Mono("OPT-IN CLIENT APP PERMISSION MONITOR", Cyan, 13, true)
        Spacer(Modifier.height(5.dp))
        Mono(
            "Only apps that you own or manage and that explicitly opt in appear here. " +
                "The dashboard polls Supabase every 5 seconds. Permission states show manifest declarations and app-reported grants.",
            Dim,
            11
        )
        Spacer(Modifier.height(8.dp))

        if (!signedIn) {
            Panel {
                Mono("SUPABASE OWNER SIGN-IN", Cyan, 14, true)
                Mono(
                    "Configure the project HTTPS URL and publishable/anon key in Accounts first. " +
                        "Sign in once with your Supabase account. The password is not saved; only encrypted session tokens are kept.",
                    Dim,
                    11
                )
                Field(email, { email = it }, "Supabase account email")
                Field(password, { password = it }, "Supabase account password", secret = true)
                NeonButton(if (busy) "SIGNING IN…" else "SIGN IN", Modifier.fillMaxWidth()) {
                    if (!busy) scope.launch {
                        busy = true
                        status = "Signing in to Supabase…"
                        try {
                            status = RegistryBackend.signIn(email, password)
                            password = ""
                            signedIn = true
                        } catch (e: Exception) {
                            status = e.message ?: "Sign-in failed."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        } else {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono("SIGNED IN", Cyan, 13, true, Modifier.weight(1f))
                    Link("SIGN OUT", Pink) {
                        if (!busy) scope.launch {
                            busy = true
                            try {
                                RegistryBackend.signOut()
                                status = "Signed out."
                            } catch (e: Exception) {
                                status = e.message ?: "Sign-out failed."
                            } finally {
                                clients = emptyList()
                                enrollmentCode = ""
                                signedIn = false
                                busy = false
                            }
                        }
                    }
                }
                Mono(email, Dim, 11)
                Mono("Live polling • refreshes every 5 seconds", Dim, 10)
            }
            Panel {
                Mono("REGISTER A MANAGED CLIENT APP", Cyan, 13, true)
                Mono(
                    "Generate a one-time code and enter it in a client app only after its user accepts that app's permission-monitoring consent screen. " +
                        "The code expires in 10 minutes and can be used once.",
                    Dim,
                    11
                )
                NeonButton(if (busy) "GENERATING…" else "GENERATE ONE-TIME ENROLLMENT CODE", Modifier.fillMaxWidth()) {
                    if (!busy) scope.launch {
                        busy = true
                        try {
                            val (code, seconds) = RegistryBackend.createEnrollmentCode()
                            enrollmentCode = code
                            codeExpirySeconds = seconds
                            status = "One-time code created."
                        } catch (e: Exception) {
                            status = e.message ?: "Could not create an enrollment code."
                        } finally {
                            busy = false
                        }
                    }
                }
                if (enrollmentCode.isNotEmpty()) {
                    Mono(enrollmentCode, Ink, 19, true, Modifier.padding(vertical = 8.dp))
                    Mono("Expires in ${codeExpirySeconds / 60}:${(codeExpirySeconds % 60).toString().padStart(2, '0')}", Pink, 11)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Mono("CONNECTED CLIENTS (${clients.size})", Cyan, 13, true)
                Link("REFRESH") { scope.launch { refreshClients() } }
            }
            if (clients.isEmpty()) {
                Panel {
                    Mono("No enrolled client apps yet.", Dim, 12)
                    Mono("Register an app with a one-time code. Its user must explicitly opt in first.", Dim, 10)
                }
            }
            clients.forEach { client ->
                ClientPermissionCard(
                    client = client,
                    onRevoke = {
                        if (!busy) scope.launch {
                            busy = true
                            try {
                                RegistryBackend.revokeClient(client.id)
                                refreshClients()
                                status = "Client enrollment revoked."
                            } catch (e: Exception) {
                                status = e.message ?: "Could not revoke enrollment."
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
            }
        }
        if (status.isNotEmpty()) Mono(status, if (status.startsWith("Updated") || status.startsWith("Signed")) Cyan else Pink, 11)
    }
}

@Composable
private fun ClientPermissionCard(client: RegisteredClient, onRevoke: () -> Unit) {
    val granted = client.permissions.count { it.status == "granted" }
    val denied = client.permissions.count { it.status == "denied" }
    val notRequested = client.permissions.count { it.status == "not_requested" }
    val connected = remember(client.lastSeen) {
        try {
            Instant.parse(client.lastSeen).isAfter(Instant.now().minus(30, ChronoUnit.SECONDS))
        } catch (_: Exception) {
            false
        }
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(client.name, Cyan, 14, true, Modifier.weight(1f))
            Mono(if (connected) "● LIVE" else "○ IDLE", if (connected) Color(0xFF247A53) else Dim, 10, true)
        }
        Mono(client.packageName, Dim, 10)
        Mono("App ID: ${client.id}", Dim, 9)
        Mono("Granted $granted  •  Denied $denied  •  Not requested $notRequested", Ink, 11, true)
        Spacer(Modifier.height(4.dp))
        if (client.permissions.isEmpty()) {
            Mono("Waiting for the enrolled app's first permission sync.", Dim, 10)
        } else {
            client.permissions.groupBy { it.category }.forEach { (category, permissions) ->
                Mono(category.uppercase(), Dim, 10, true, Modifier.padding(top = 5.dp))
                permissions.forEach { permission ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Mono(permission.name.removePrefix("android.permission."), Ink, 9, modifier = Modifier.weight(1f))
                        Mono(permission.status.replace('_', ' ').uppercase(), stateColor(permission.status), 9, true)
                    }
                }
            }
        }
        Link("REVOKE CLIENT ACCESS", Pink, onRevoke)
    }
}

private fun stateColor(state: String): Color = when (state) {
    "granted" -> Color(0xFF247A53)
    "denied" -> Pink
    else -> Dim
}
