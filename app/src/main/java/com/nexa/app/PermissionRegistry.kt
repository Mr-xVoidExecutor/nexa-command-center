package com.nexa.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.app.AppOpsManager
import android.os.Process
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

enum class PermissionState {
    GRANTED,
    DENIED,
    NOT_REQUESTED
}

data class PermissionRecord(
    val permission: String,
    val category: String,
    val state: PermissionState
) {
    fun toJson(): JSONObject = JSONObject()
        .put("permission", permission)
        .put("category", category)
        .put("status", state.name.lowercase())
}

object PermissionCatalog {
    private val categories = linkedMapOf(
        "Calendar" to listOf(
            "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR"
        ),
        "Camera" to listOf(Manifest.permission.CAMERA),
        "Contacts" to listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS
        ),
        "Location" to listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ),
        "Microphone" to listOf(Manifest.permission.RECORD_AUDIO),
        "Phone" to listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ADD_VOICEMAIL,
            Manifest.permission.USE_SIP,
            Manifest.permission.PROCESS_OUTGOING_CALLS
        ),
        "Health" to listOf(
            Manifest.permission.BODY_SENSORS,
            "android.permission.BODY_SENSORS_BACKGROUND"
        ),
        "SMS" to listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.RECEIVE_MMS
        ),
        "Storage and media" to listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        ),
        "Network" to listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        ),
        "Bluetooth" to listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE"
        ),
        "Nearby devices" to listOf(
            Manifest.permission.NFC,
            "android.permission.UWB_RANGING"
        ),
        "Notifications" to listOf(
            Manifest.permission.ACCESS_NOTIFICATION_POLICY,
            "android.permission.POST_NOTIFICATIONS"
        ),
        "Package management" to listOf(
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            "android.permission.REQUEST_DELETE_PACKAGES"
        ),
        "System settings" to listOf(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.WRITE_SETTINGS,
            Manifest.permission.CHANGE_CONFIGURATION,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.VIBRATE,
            "android.permission.FLASHLIGHT",
            Manifest.permission.DISABLE_KEYGUARD,
            Manifest.permission.USE_BIOMETRIC,
            "android.permission.USE_FULL_SCREEN_INTENT"
        ),
        "Special access" to listOf(
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.BIND_ACCESSIBILITY_SERVICE"
        )
    )

    val allPermissions: Set<String> = categories.values.flatten().toSet()
    fun category(permission: String): String =
        categories.entries.firstOrNull { permission in it.value }?.key ?: "Other"
}

/**
 * Embed this helper only in client apps that you own or manage. Show a clear
 * consent screen before calling enroll() or sync(). The enrollment code is
 * one-time; its replacement app token is stored in this app's Keystore-backed
 * EncryptedSharedPreferences. Never ship a service-role or master key in a client.
 */
class PermissionRegistryClient(
    context: Context,
    private val supabaseUrl: String,
    private val supabaseAnonKey: String
) {
    private val appContext = context.applicationContext
    private val secureStore = RegistryInstallStore(appContext)
    private val endpoint = "${supabaseUrl.trimEnd('/')}/functions/v1/permission-registry"

    suspend fun enroll(oneTimeCode: String): String {
        require(supabaseUrl.startsWith("https://")) { "A valid HTTPS Supabase URL is required." }
        require(supabaseAnonKey.isNotBlank()) { "A Supabase publishable/anon key is required." }
        require(oneTimeCode.isNotBlank()) { "Enter the one-time enrollment code." }
        check(!secureStore.isEnrolled()) {
            "This install is already enrolled. Revoke its enrollment before registering again."
        }

        val body = JSONObject()
            .put("action", "redeem_enrollment")
            .put("code", oneTimeCode.trim())
            .put("package_name", appContext.packageName)
            .put("app_name", appContext.applicationInfo.loadLabel(appContext.packageManager).toString())
        val (status, response) = Net.call(
            endpoint,
            method = "POST",
            body = body.toString(),
            headers = mapOf("apikey" to supabaseAnonKey, "Authorization" to "Bearer $supabaseAnonKey")
        )
        if (status !in 200..299) error("Enrollment failed ($status): ${response.take(200)}")
        val result = JSONObject(response)
        val clientId = result.getString("client_app_id")
        secureStore.put(clientId, result.getString("client_token"))
        secureStore.markEnrolled()
        return clientId
    }

    fun isEnrolled(): Boolean = secureStore.isEnrolled()
    fun clientAppId(): String = secureStore.clientAppId()

    fun recordPermissionRequest(permission: String, granted: Boolean) {
        if (permission !in PermissionCatalog.allPermissions) {
            error("Permission is not in the Android permission registry catalog.")
        }
        secureStore.recordRequest(permission, granted)
    }

    fun collectDeclaredPermissions(): List<PermissionRecord> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
            )
        }
        val requested = packageInfo.requestedPermissions.orEmpty()
            .filter { it in PermissionCatalog.allPermissions }
            .toMutableSet()
        if (packageInfo.services.orEmpty().any {
                it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
            }) {
            requested.add("android.permission.BIND_ACCESSIBILITY_SERVICE")
        }
        val ordered = requested.sorted()
        return ordered.map { permission ->
            PermissionRecord(
                permission,
                PermissionCatalog.category(permission),
                permissionState(permission)
            )
        }
    }

    suspend fun sync(): Int {
        check(secureStore.isEnrolled()) { "This app has not been enrolled." }
        val body = JSONObject()
            .put("action", "sync")
            .put("client_app_id", secureStore.clientAppId())
            .put("permissions", JSONArray().apply {
                collectDeclaredPermissions().forEach { put(it.toJson()) }
            })
        val token = secureStore.token()
        val (status, response) = Net.call(
            endpoint,
            method = "POST",
            body = body.toString(),
            headers = mapOf("apikey" to supabaseAnonKey, "Authorization" to "Bearer $token")
        )
        if (status !in 200..299) error("Permission sync failed ($status): ${response.take(200)}")
        return JSONObject(response).optInt("updated", 0)
    }

    suspend fun revoke() {
        check(secureStore.isEnrolled()) { "This app has not been enrolled." }
        val body = JSONObject()
            .put("action", "revoke")
            .put("client_app_id", secureStore.clientAppId())
        val (status, response) = Net.call(
            endpoint,
            method = "POST",
            body = body.toString(),
            headers = mapOf("apikey" to supabaseAnonKey, "Authorization" to "Bearer ${secureStore.token()}")
        )
        if (status !in 200..299) error("Could not revoke registry access ($status): ${response.take(200)}")
        secureStore.clearEnrollment()
    }

    private fun permissionState(permission: String): PermissionState {
        val granted = when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(appContext)
            Manifest.permission.WRITE_SETTINGS -> Settings.System.canWrite(appContext)
            "android.permission.MANAGE_EXTERNAL_STORAGE" ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()
            Manifest.permission.ACCESS_NOTIFICATION_POLICY ->
                (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .isNotificationPolicyAccessGranted
            Manifest.permission.REQUEST_INSTALL_PACKAGES ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appContext.packageManager.canRequestPackageInstalls()
            "android.permission.PACKAGE_USAGE_STATS" -> hasUsageAccess()
            "android.permission.BIND_ACCESSIBILITY_SERVICE" -> hasEnabledAccessibilityService()
            "android.permission.USE_FULL_SCREEN_INTENT" ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .canUseFullScreenIntent()
            else -> ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) return PermissionState.GRANTED
        return if (secureStore.wasRequested(permission)) PermissionState.DENIED else PermissionState.NOT_REQUESTED
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasEnabledAccessibilityService(): Boolean {
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.substringBefore('/').equals(appContext.packageName, ignoreCase = true) }
    }
}

private class RegistryInstallStore(context: Context) {
    private val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
        context,
        "nexa_permission_registry_install",
        androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build(),
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun put(clientId: String, clientToken: String) {
        prefs.edit()
            .putString("client_app_id", clientId)
            .putString("client_token", clientToken)
            .apply()
    }

    fun markEnrolled() { prefs.edit().putBoolean("enrolled", true).apply() }
    fun isEnrolled(): Boolean = prefs.getBoolean("enrolled", false)
    fun clientAppId(): String = prefs.getString("client_app_id", "").orEmpty()
    fun token(): String = prefs.getString("client_token", "").orEmpty()

    fun recordRequest(permission: String, granted: Boolean) {
        prefs.edit().putBoolean("requested:$permission", granted).apply()
    }

    fun wasRequested(permission: String): Boolean = prefs.contains("requested:$permission")

    fun clearEnrollment() {
        prefs.edit()
            .remove("client_app_id")
            .remove("client_token")
            .remove("enrolled")
            .apply()
    }
}
