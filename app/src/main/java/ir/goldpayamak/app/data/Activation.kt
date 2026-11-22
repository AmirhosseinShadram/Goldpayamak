package ir.goldpayamak.app.data

import android.content.Context
import android.provider.Settings
import ir.goldpayamak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ActivationResult {
    data object Ok : ActivationResult
    data object Invalid : ActivationResult
    data object Used : ActivationResult
    data object TooMany : ActivationResult
    data object NotConfigured : ActivationResult
    data class NetworkError(val detail: String) : ActivationResult
}

/** اعتبارسنجی کد یک‌بارمصرف روی سرور؛ کد بعد از اولین فعال‌سازی موفق مصرف می‌شود. */
object Activation {
    suspend fun activate(ctx: Context, code: String): ActivationResult = withContext(Dispatchers.IO) {
        if (BuildConfig.ACTIVATION_URL.contains("CHANGE-ME")) return@withContext ActivationResult.NotConfigured
        val device = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        try {
            val conn = (URL(BuildConfig.ACTIVATION_URL + "/api/activate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(JSONObject().put("code", code).put("deviceId", device).toString().toByteArray()) }
            val result = when (conn.responseCode) {
                200 -> ActivationResult.Ok
                404 -> ActivationResult.Invalid
                409 -> ActivationResult.Used
                429 -> ActivationResult.TooMany
                else -> ActivationResult.NetworkError("کد پاسخ ${conn.responseCode}")
            }
            conn.disconnect()
            result
        } catch (e: Exception) {
            ActivationResult.NetworkError(e.javaClass.simpleName)
        }
    }
}
