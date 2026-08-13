package com.calculator.vault

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import com.calculator.vault.managers.CalendarManager
import com.calculator.vault.managers.ContactManager
import com.calculator.vault.managers.FileManager
import com.calculator.vault.managers.NotificationManager
import com.calculator.vault.security.EncryptionHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Bridges the JS calculator UI (assets/vault/index.html, window.vaultNative /
 * window.nativeStorage) to the native managers. Every method the JS side calls
 * on `AndroidBridge` must exist here with the exact name/arity it expects.
 */
class WebAppInterface(
    private val activity: Activity,
    private val webView: WebView,
    private val encryptionHelper: EncryptionHelper
) {

    companion object {
        const val FILE_CHOOSER_REQUEST = 1000
        const val CAPTURE_IMAGE_REQUEST = 1001
        const val CAPTURE_VIDEO_REQUEST = 1002
    }

    var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileManager by lazy { FileManager(activity) }
    private val contactManager by lazy { ContactManager(activity) }
    private val calendarManager by lazy { CalendarManager(activity) }
    private val notificationManager by lazy { NotificationManager(activity) }

    // ---- JSON response helpers -------------------------------------------------
    // The JS side always does JSON.parse(...) on string returns and expects
    // {"success": true, "data": ...} or {"success": false, "error": "..."}.

    private fun success(data: Any?): String =
        JSONObject().put("success", true).put("data", toJsonValue(data)).toString()

    private fun failure(message: String?): String =
        JSONObject().put("success", false).put("error", (message ?: "Unknown error")).toString()

    private fun toJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (k, v) -> put(k.toString(), toJsonValue(v)) }
        }
        is List<*> -> JSONArray().apply {
            value.forEach { put(toJsonValue(it)) }
        }
        else -> value
    }

    // ---- Vault data (backs window.nativeStorage / localStorage shim) ----------

    @JavascriptInterface
    fun setPin(pin: String): String = try {
        encryptionHelper.setPin(pin)
        success(mapOf("set" to true))
    } catch (e: Exception) {
        failure(e.message)
    }

    @JavascriptInterface
    fun verifyPin(pin: String): String = try {
        success(mapOf("valid" to encryptionHelper.verifyPin(pin)))
    } catch (e: Exception) {
        failure(e.message)
    }

    @JavascriptInterface
    fun storeVaultData(key: String, value: String, pin: String) {
        try {
            encryptionHelper.storeVaultData(key, value, pin)
        } catch (e: Exception) {
            // Fire-and-forget from the JS side (setItem has no return value),
            // so just avoid crashing the bridge thread on failure.
        }
    }

    @JavascriptInterface
    fun retrieveVaultData(key: String, pin: String): String = try {
        val data = encryptionHelper.retrieveVaultData(key, pin)
        if (data != null) success(mapOf("data" to data)) else failure("Not found or wrong PIN")
    } catch (e: Exception) {
        failure(e.message)
    }

    @JavascriptInterface
    fun deleteVaultData(key: String) {
        try {
            activity.getSharedPreferences("vault_data", Context.MODE_PRIVATE)
                .edit().remove(key).apply()
        } catch (e: Exception) {
        }
    }

    // ---- Files ------------------------------------------------------------------

    @JavascriptInterface
    fun getStorageInfo(): String = try {
        success(fileManager.getStorageInfo())
    } catch (e: Exception) {
        failure(e.message)
    }

    @JavascriptInterface
    fun listFiles(path: String): String = try {
        success(fileManager.listFiles(path))
    } catch (e: Exception) {
        failure(e.message)
    }

    // ---- Contacts / Calendar -----------------------------------------------------

    @JavascriptInterface
    fun importContacts(): String = try {
        success(contactManager.importContacts())
    } catch (e: Exception) {
        failure(e.message)
    }

    @JavascriptInterface
    fun getCalendarEvents(start: Long, end: Long): String = try {
        success(calendarManager.getEvents(start, end))
    } catch (e: Exception) {
        failure(e.message)
    }

    // ---- Notifications / misc -----------------------------------------------------

    @JavascriptInterface
    fun scheduleNotification(title: String, message: String, triggerTime: Long) {
        try {
            notificationManager.scheduleNotification(title, message, triggerTime)
        } catch (e: Exception) {
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Installed app picker (for the in-vault hidden-app shortcuts) ------------

    private fun drawableToBase64(drawable: Drawable): String {
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return "data:image/png;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Returns every launchable app on the device (name, package, icon as a
     * data: URI) so the vault UI can show a real picker instead of asking
     * the person to type a package name or intent URI by hand.
     */
    @JavascriptInterface
    fun getInstalledApps(): String = try {
        val pm = activity.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedApps = pm.queryIntentActivities(mainIntent, 0)

        val apps = resolvedApps
            .filter { it.activityInfo.packageName != activity.packageName } // don't list itself
            .map { resolveInfo ->
                val appInfo: ApplicationInfo = resolveInfo.activityInfo.applicationInfo
                mapOf(
                    "name" to resolveInfo.loadLabel(pm).toString(),
                    "packageName" to resolveInfo.activityInfo.packageName,
                    "icon" to drawableToBase64(resolveInfo.loadIcon(pm))
                )
            }
            .distinctBy { it["packageName"] }
            .sortedBy { (it["name"] as String).lowercase() }

        success(apps)
    } catch (e: Exception) {
        failure(e.message)
    }

    /**
     * Launches another app the proper way (a real Android Intent), rather
     * than treating it as a URL the WebView tries to navigate to.
     */
    @JavascriptInterface
    fun launchInstalledApp(packageName: String): String = try {
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(launchIntent)
            success(mapOf("launched" to true))
        } else {
            failure("That app is no longer installed.")
        }
    } catch (e: Exception) {
        failure(e.message)
    }
}
