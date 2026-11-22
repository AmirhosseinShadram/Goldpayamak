package ir.goldpayamak.app.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** تنظیمات محلی و قفل نام‌کاربری/رمز (رمز با PBKDF2 و salt ذخیره می‌شود). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("gp", Context.MODE_PRIVATE)

    val isSetUp: Boolean get() = sp.contains("hash")
    var displayName: String
        get() = sp.getString("display", "") ?: ""
        set(v) = sp.edit().putString("display", v.trim()).apply()
    val username: String get() = sp.getString("user", "") ?: ""

    /** subscriptionId سیم‌کارت انتخابی؛ -1 یعنی پیش‌فرض سیستم */
    var subId: Int
        get() = sp.getInt("subId", -1)
        set(v) = sp.edit().putInt("subId", v).apply()

    var delaySeconds: Int
        get() = sp.getInt("delay", 3)
        set(v) = sp.edit().putInt("delay", v.coerceIn(1, 60)).apply()

    fun setCredentials(user: String, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("user", user.trim())
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(password, salt), Base64.NO_WRAP))
            .apply()
    }

    fun check(user: String, password: String): Boolean {
        val salt = Base64.decode(sp.getString("salt", "") ?: return false, Base64.NO_WRAP)
        val stored = Base64.decode(sp.getString("hash", "") ?: return false, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(hash(password, salt), stored)
        return ok && user.trim() == username
    }

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
}
