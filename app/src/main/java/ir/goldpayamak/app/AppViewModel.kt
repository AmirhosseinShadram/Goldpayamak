package ir.goldpayamak.app

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.goldpayamak.app.data.*
import ir.goldpayamak.app.sms.SendService
import ir.goldpayamak.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val repo = Repo(app)
    val dao get() = repo.dao

    val offlineRequestCode = if (BuildConfig.OFFLINE_LICENSE) {
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { OfflineLicense.requestCode(app.packageName, it) }
    } else null
    var isActivated by mutableStateOf(
        if (BuildConfig.OFFLINE_LICENSE) isValidOfflineLicense(prefs.offlineLicense) else prefs.isActivated
    )
        private set
    var displayName by mutableStateOf(prefs.displayName)
    var senderNames by mutableStateOf(prefs.senderNames)
        private set
    val defaultSenderName: String get() = senderNames.firstOrNull().orEmpty()

    val contacts = dao.contacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goldMessages = dao.goldMessages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selfMessages = dao.selfMessages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val roots = dao.roots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val actions = dao.actions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val requests = dao.requests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** پیام خطای قابل‌نمایش به کاربر */
    var errorMsg by mutableStateOf<String?>(null)

    private fun guarded(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) {
            errorMsg = e.message?.takeIf { it.any { c -> c in '\u0600'..'\u06FF' } } ?: "خطا در انجام عملیات. دوباره تلاش کنید."
        }
    }

    init {
        // پیام‌هایی که اجرای قبلی نیمه‌کاره رها کرده بود (برنامه بسته شده) شکست‌خورده حساب می‌شوند تا قابل ارسال مجدد باشند.
        viewModelScope.launch(Dispatchers.IO) {
            if (SendService.active.get() == 0) dao.failStale(System.currentTimeMillis() - 60_000L)
        }
    }

    var activating by mutableStateOf(false)

    /** فقط در نسخهٔ دمو: بدون کد فعال‌سازی */
    fun activateDemo(name: String) {
        check(BuildConfig.DEMO)
        prefs.markActivated(name)
        displayName = prefs.displayName
        senderNames = prefs.senderNames
        isActivated = true
    }

    /** اعتبارسنجی کد روی سرور؛ در صورت موفقیت نام ذخیره و برنامه برای همیشه (تا حذف برنامه) فعال می‌شود. */
    fun activate(name: String, code: String, onResult: (ActivationResult) -> Unit) {
        check(!BuildConfig.OFFLINE_LICENSE)
        activating = true
        viewModelScope.launch {
            val r = Activation.activate(app, code)
            if (r is ActivationResult.Ok) {
                prefs.markActivated(name)
                displayName = prefs.displayName
                senderNames = prefs.senderNames
                isActivated = true
            }
            activating = false
            onResult(r)
        }
    }

    private fun isValidOfflineLicense(license: String): Boolean =
        offlineRequestCode?.let { OfflineLicense.verify(BuildConfig.LICENSE_PUBLIC_KEY, it, license) } == true

    fun activateOffline(name: String, license: String): Boolean {
        if (!BuildConfig.OFFLINE_LICENSE || name.isBlank() || !isValidOfflineLicense(license)) return false
        prefs.saveOfflineLicense(name, license)
        displayName = prefs.displayName
        senderNames = prefs.senderNames
        isActivated = true
        return true
    }

    // ---- درخواست‌ها ----
    fun sendNew(ring: List<Party>, amount: TransferAmount, onDone: (Long) -> Unit) = guarded {
        val id = repo.createOriginal(ring[Ring.USER_INDEX].name, ring, amount)
        SendService.start(app, id)
        rememberSender(ring[Ring.USER_INDEX].name)
        onDone(id)
    }

    fun sendCorrection(parentId: Long, ring: List<Party>, amount: TransferAmount, note: String, onDone: (Long) -> Unit) = guarded {
        val id = repo.createCorrection(parentId, ring[Ring.USER_INDEX].name, ring, amount, note)
        SendService.start(app, id)
        rememberSender(ring[Ring.USER_INDEX].name)
        onDone(id)
    }

    fun sendGold(senderName: String, d: GoldData, onDone: (Long) -> Unit) = guarded {
        val id = repo.createGold(senderName, d)
        SendService.start(app, id)
        rememberSender(senderName)
        onDone(id)
    }

    fun sendGoldCorrection(parentId: Long, senderName: String, d: GoldData, note: String, onDone: (Long) -> Unit) = guarded {
        val id = repo.createGoldCorrection(parentId, senderName, d, note)
        SendService.start(app, id)
        rememberSender(senderName)
        onDone(id)
    }

    suspend fun goldOf(id: Long) = repo.goldOf(id)

    private fun rememberSender(name: String) {
        prefs.rememberSender(name)
        senderNames = prefs.senderNames
    }

    fun removeSender(name: String) {
        prefs.removeSender(name)
        senderNames = prefs.senderNames
    }

    /** حذف همهٔ مخاطبین، درخواست‌ها و پیام‌ها (فعال‌سازی و نام باقی می‌ماند) */
    fun clearAll(onDone: () -> Unit) = guarded {
        withContext(Dispatchers.IO) { AppDb.get(app).clearAllTables() }
        onDone()
    }

    fun cancel(parentId: Long, note: String, onDone: (Long) -> Unit) = guarded {
        val id = repo.createCancel(parentId, note)
        SendService.start(app, id)
        onDone(id)
    }

    fun retry(requestId: Long) = guarded {
        dao.resetFailed(requestId, System.currentTimeMillis())
        SendService.start(app, requestId)
    }

    suspend fun ringOf(id: Long) = repo.ringOf(id)
    suspend fun requestOnce(id: Long) = dao.requestOnce(id)

    // ---- مخاطبین ----
    fun saveContact(c: ContactEntity?, name: String, phone: String) = guarded {
        val existing = dao.contactByPhone(phone)
        if (existing != null && existing.id != c?.id) error("این شماره قبلاً برای «${existing.name}» ذخیره شده است.")
        if (c == null) dao.insertContactIgnore(ContactEntity(name = name.trim(), phone = phone))
        else dao.updateContact(c.copy(name = name.trim(), phone = phone))
    }

    fun deleteContact(c: ContactEntity) = guarded { dao.deleteContact(c) }

    // ---- گزارش ----
    fun exportPdf(uri: Uri, from: Long, to: Long, fromText: String, toText: String, type: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val groups = groupReport(dao.requestsBetweenOnce(from, to), dao.messagesBetweenOnce(from, to)).filter { it.request.type == type }
                app.contentResolver.openOutputStream(uri, "wt")!!.use {
                    ir.goldpayamak.app.ui.PdfReport.write(it, fromText, toText, groups, if (type == "GOLD") "گزارش طلایی" else "گزارش حواله")
                }
                true
            } catch (e: Exception) { false }
        }
        onDone(ok)
    }
}

fun kindLabel(k: String) = when (k) {
    "ORIGINAL" -> "اصلی"
    "CORRECTION" -> "اصلاحیه"
    "CANCEL" -> "کنسلی"
    else -> k
}

fun statusLabel(s: String) = when (s) {
    Status.PENDING -> "در صف ارسال"
    Status.SENDING -> "در حال ارسال"
    Status.SENT -> "ارسال شد"
    Status.DELIVERED -> "تحویل شد"
    Status.FAILED -> "ناموفق"
    Status.SELF -> "ثبت (طرف ۲، ارسال نمی‌شود)"
    Status.REQ_ACTIVE -> "فعال"
    Status.REQ_SUPERSEDED -> "اصلاح شده"
    Status.REQ_CANCELLED -> "کنسل شده"
    Status.REQ_RECORD -> "ثبت شد"
    else -> s
}
