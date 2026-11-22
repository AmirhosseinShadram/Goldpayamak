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

    /** نسخهٔ قبلی نام‌کاربری/رمز داشت؛ کلید جدید جدا نگه داشته می‌شود تا نصب قدیمی دوباره کد بگیرد ولی داده‌ها بماند. */
    val isSetUp: Boolean get() = sp.contains("code_hash")
    var displayName: String
        get() = sp.getString("display", "") ?: ""
        set(v) = sp.edit().putString("display", v.trim()).apply()

    /** subscriptionId سیم‌کارت انتخابی؛ -1 یعنی پیش‌فرض سیستم */
    var subId: Int
        get() = sp.getInt("subId", -1)
        set(v) = sp.edit().putInt("subId", v).apply()

    var delaySeconds: Int
        get() = sp.getInt("delay", 3)
        set(v) = sp.edit().putInt("delay", v.coerceIn(1, 60)).apply()

    /** کد ورود ۱۲ رقمی؛ فقط هش PBKDF2 با salt ذخیره می‌شود. */
    fun setCode(code: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("code_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("code_hash", Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .apply()
    }

    fun checkCode(code: String): Boolean {
        val salt = Base64.decode(sp.getString("code_salt", "") ?: return false, Base64.NO_WRAP)
        val stored = Base64.decode(sp.getString("code_hash", "") ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(code, salt), stored)
    }

    private fun hash(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, 120_000, 256)).encoded
}
