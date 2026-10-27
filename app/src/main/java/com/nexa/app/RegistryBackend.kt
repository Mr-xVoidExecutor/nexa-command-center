package com.nexa.app

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class RegisteredClient(
    val id: String,
    val name: String,
    val packageName: String,
    val lastSeen: String,
    val permissions: List<RegisteredPermission>
)

data class RegisteredPermission(
    val name: String,
    val category: String,
    val status: String
)

object RegistryBackend {
    private var accessToken = ""
    private var accessTokenExpiresAt = 0L

    fun signedIn(): Boolean = Store.get("registryRefreshToken").isNotBlank()
    fun email(): String = Store.get("registryEmail")

    suspend fun signIn(email: String, password: String): String {
        requireConfiguration()
        val (status, body) = Net.call(
            "${baseUrl()}/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password).toString(),
            headers = mapOf("apikey" to anonKey())
        )
        if (status !in 200..299) {
            throw IOException("Sign-in failed ($status): ${errorMessage(body)}")
        }
        val session = JSONObject(body)
        acceptSession(session)
        Store.put("registryEmail", email.trim())
        return "Signed in as ${email.trim()}."
    }

    suspend fun restoreSession(): Boolean {
        val refresh = Store.get("registryRefreshToken")
        if (refresh.isBlank()) return false
        getAccessToken()
        return true
    }

    suspend fun signOut() {
        var revokeFailure: String? = null
        try {
            val token = getAccessToken()
            val (status, body) = Net.call(
                "${baseUrl()}/auth/v1/logout",
                method = "POST",
                headers = mapOf("apikey" to anonKey(), "Authorization" to "Bearer $token")
            )
            if (status !in 200..299) {
                revokeFailure = "Supabase sign-out failed ($status): ${errorMessage(body)}"
            }
        } catch (e: Exception) {
            revokeFailure = e.message ?: "Could not revoke the Supabase session."
        } finally {
            clearSession()
        }
        if (revokeFailure != null) {
            throw IOException("Signed out on this device, but server session revocation failed: $revokeFailure")
        }
    }

    suspend fun createEnrollmentCode(): Pair<String, Int> {
        val result = callFunction(JSONObject().put("action", "create_enrollment"))
        return result.getString("code") to result.optInt("expires_in_seconds", 600)
    }

    suspend fun loadClients(): List<RegisteredClient> {
        val token = getAccessToken()
        val clientsUrl = "${baseUrl()}/rest/v1/permission_clients" +
            "?select=client_app_id,app_name,package_name,last_seen_at,revoked_at" +
            "&revoked_at=is.null&order=last_seen_at.desc.nullslast"
        val clients = getArray(clientsUrl, token)
        val snapshotsUrl = "${baseUrl()}/rest/v1/permission_snapshots" +
            "?select=client_app_id,permission,category,status&order=client_app_id.asc,permission.asc"
        val snapshots = getArray(snapshotsUrl, token)
        val permissionsByClient = mutableMapOf<String, MutableList<RegisteredPermission>>()
        for (index in 0 until snapshots.length()) {
            val permission = snapshots.getJSONObject(index)
            permissionsByClient.getOrPut(permission.getString("client_app_id")) { mutableListOf() }
                .add(
                    RegisteredPermission(
                        name = permission.optString("permission"),
                        category = permission.optString("category", "Other"),
                        status = permission.optString("status", "not_requested")
                    )
                )
        }
        return buildList {
            for (index in 0 until clients.length()) {
                val client = clients.getJSONObject(index)
                val id = client.getString("client_app_id")
                add(
                    RegisteredClient(
                        id = id,
                        name = client.optString("app_name", "Client app"),
                        packageName = client.optString("package_name"),
                        lastSeen = client.optString("last_seen_at"),
                        permissions = permissionsByClient[id].orEmpty()
                    )
                )
            }
        }
    }

    suspend fun revokeClient(id: String) {
        callFunction(JSONObject().put("action", "revoke_client").put("client_app_id", id))
    }

    private suspend fun callFunction(body: JSONObject): JSONObject {
        val token = getAccessToken()
        val (status, response) = Net.call(
            "${baseUrl()}/functions/v1/permission-registry",
            method = "POST",
            body = body.toString(),
            headers = mapOf(
                "apikey" to anonKey(),
                "Authorization" to "Bearer $token"
            )
        )
        if (status !in 200..299) {
            throw IOException("Registry request failed ($status): ${errorMessage(response)}")
        }
        return JSONObject(response)
    }

    private suspend fun getArray(url: String, token: String): JSONArray {
        val combined = JSONArray()
        var offset = 0
        val pageSize = 500
        while (true) {
            val (status, body) = Net.call(
                url,
                headers = mapOf(
                    "apikey" to anonKey(),
                    "Authorization" to "Bearer $token",
                    "Range-Unit" to "items",
                    "Range" to "$offset-${offset + pageSize - 1}"
                )
            )
            if (status !in 200..299) {
                throw IOException("Could not load registry data ($status): ${errorMessage(body)}")
            }
            val page = JSONArray(body)
            for (index in 0 until page.length()) combined.put(page.get(index))
            if (page.length() < pageSize) return combined
            offset += pageSize
        }
    }

    private suspend fun getAccessToken(): String {
        requireConfiguration()
        val storedToken = Store.get("registryAccessToken")
        val storedExpiry = Store.get("registryAccessExpiresAt").toLongOrNull() ?: 0L
        if (storedToken.isNotBlank() && storedExpiry > System.currentTimeMillis() + 60_000L) {
            accessToken = storedToken
            accessTokenExpiresAt = storedExpiry
            return storedToken
        }
        if (accessToken.isNotBlank() && accessTokenExpiresAt > System.currentTimeMillis() + 60_000L) {
            return accessToken
        }

        val refresh = Store.get("registryRefreshToken")
        if (refresh.isBlank()) throw IOException("Sign in to your Supabase account first.")
        val (status, body) = Net.call(
            "${baseUrl()}/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", refresh).toString(),
            headers = mapOf("apikey" to anonKey())
        )
        if (status !in 200..299) {
            clearSession()
            throw IOException("Supabase session expired ($status). Sign in again.")
        }
        acceptSession(JSONObject(body))
        return accessToken
    }

    private fun acceptSession(session: JSONObject) {
        val newAccessToken = session.optString("access_token")
        val newRefreshToken = session.optString("refresh_token")
        if (newAccessToken.isBlank() || newRefreshToken.isBlank()) {
            throw IOException("Supabase returned an incomplete sign-in session.")
        }
        val expiresIn = session.optLong("expires_in", 3600).coerceIn(60, 86_400)
        val expiry = System.currentTimeMillis() + expiresIn * 1000L
        accessToken = newAccessToken
        accessTokenExpiresAt = expiry
        Store.put("registryAccessToken", newAccessToken)
        Store.put("registryRefreshToken", newRefreshToken)
        Store.put("registryAccessExpiresAt", expiry.toString())
    }

    private fun baseUrl(): String = Store.get("supaUrl").trimEnd('/')
    private fun anonKey(): String = Store.get("supaKey").trim()

    private fun clearSession() {
        Store.put("registryAccessToken", "")
        Store.put("registryRefreshToken", "")
        Store.put("registryAccessExpiresAt", "")
        Store.put("registryEmail", "")
        accessToken = ""
        accessTokenExpiresAt = 0L
    }

    private fun requireConfiguration() {
        if (!baseUrl().startsWith("https://") || anonKey().isBlank()) {
            throw IOException("Configure your Supabase project HTTPS URL and publishable/anon key in Accounts.")
        }
    }

    private fun errorMessage(body: String): String = try {
        val error = JSONObject(body)
        error.optString("msg").ifBlank {
            error.optString("message").ifBlank {
                error.optString("error_description").ifBlank { "Request rejected by Supabase." }
            }
        }.take(220)
    } catch (e: Exception) {
        Log.w("NEXA", "Supabase returned a non-JSON error", e)
        "Supabase returned an unreadable error response."
    }
}
