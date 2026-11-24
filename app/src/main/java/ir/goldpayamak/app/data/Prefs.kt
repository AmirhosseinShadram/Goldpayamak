package ir.goldpayamak.app.data

import android.content.Context
import org.json.JSONArray

/** تنظیمات محلی و وضعیت فعال‌سازی. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("gp", Context.MODE_PRIVATE)

    /** پس از یک‌بار فعال‌سازی موفق true می‌شود و تا حذف برنامه (پاک شدن SharedPreferences) دیگر کد خواسته نمی‌شود. */
    val isActivated: Boolean get() = sp.getBoolean("activated", false)
    fun markActivated(name: String) { sp.edit().putString("display", name.trim()).putBoolean("activated", true).apply() }

    val displayName: String get() = sp.getString("display", "").orEmpty()

    val senderNames: List<String>
        get() {
            val names = JSONArray(sp.getString("senderNames", "[]") ?: "[]")
            val recent = List(names.length()) { names.getString(it) }
            val initialName = displayName
            return if (initialName.isNotBlank() && !sp.getBoolean("initialSenderRemoved", false)) {
                (recent + initialName).distinct()
            } else recent
        }

    fun rememberSender(name: String) {
        val names = listOf(name) + senderNames.filterNot { it == name }
        sp.edit().putString("senderNames", JSONArray(names).toString()).apply()
    }

    fun removeSender(name: String) {
        val names = senderNames.filterNot { it == name }
        val edit = sp.edit().putString("senderNames", JSONArray(names).toString())
        if (name == displayName) edit.putBoolean("initialSenderRemoved", true)
        edit.apply()
    }

    /** subscriptionId سیم‌کارت انتخابی؛ -1 یعنی پیش‌فرض سیستم */
    var subId: Int
        get() = sp.getInt("subId", -1)
        set(v) = sp.edit().putInt("subId", v).apply()

    var delaySeconds: Int
        get() = sp.getInt("delay", 3)
        set(v) = sp.edit().putInt("delay", v.coerceIn(1, 60)).apply()
}
