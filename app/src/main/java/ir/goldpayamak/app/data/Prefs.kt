package ir.goldpayamak.app.data

import android.content.Context

/** تنظیمات محلی و وضعیت فعال‌سازی. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("gp", Context.MODE_PRIVATE)

    /** پس از یک‌بار فعال‌سازی موفق true می‌شود و تا حذف برنامه (پاک شدن SharedPreferences) دیگر کد خواسته نمی‌شود. */
    val isActivated: Boolean get() = sp.getBoolean("activated", false)
    fun markActivated(name: String) { sp.edit().putString("display", name.trim()).putBoolean("activated", true).apply() }

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
}
