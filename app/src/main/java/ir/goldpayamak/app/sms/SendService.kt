package ir.goldpayamak.app.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import ir.goldpayamak.app.R
import ir.goldpayamak.app.data.AppDb
import ir.goldpayamak.app.data.MessageEntity
import ir.goldpayamak.app.data.Prefs
import kotlinx.coroutines.*

/** ارسال پیامک‌های در صف یک درخواست، یکی‌یکی و با وقفه، مستقیم از سیم‌کارت گوشی. */
class SendService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = HashSet<Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val rid = intent?.getLongExtra(EXTRA_REQUEST, -1L) ?: -1L
        if (rid < 0) { stopSelf(startId); return START_NOT_STICKY }
        synchronized(running) { if (!running.add(rid)) return START_NOT_STICKY }
        active.incrementAndGet()
        scope.launch {
            try { process(rid) } finally {
                active.decrementAndGet()
                synchronized(running) { running.remove(rid) }
                if (synchronized(running) { running.isEmpty() }) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun process(rid: Long) {
        val dao = AppDb.get(this).dao()
        val prefs = Prefs(this)
        val list = dao.pendingOf(rid)
        for ((i, m) in list.withIndex()) {
            sendOne(m, prefs.subId)
            if (i < list.lastIndex) delay(prefs.delaySeconds * 1000L)
        }
    }

    private suspend fun sendOne(m: MessageEntity, subId: Int) {
        val dao = AppDb.get(this).dao()
        val now = System.currentTimeMillis()
        try {
            val sm = smsManager(subId)
            val parts = sm.divideMessage(m.text)
            dao.markSending(m.id, "SENDING", parts.size, now)
            val sentPi = pending(m.id, ACTION_SENT, 0)
            val delPi = pending(m.id, ACTION_DELIVERED, 1)
            val sentList = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(sentPi) } }
            val delList = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(delPi) } }
            sm.sendMultipartTextMessage(m.phone, null, parts, sentList, delList)
        } catch (e: Exception) {
            dao.markFailedNow(m.id, System.currentTimeMillis())
        }
    }

    private fun pending(msgId: Long, action: String, salt: Int): PendingIntent {
        val i = Intent(this, SmsStatusReceiver::class.java).setAction(action).putExtra(EXTRA_MESSAGE, msgId)
        // هر پیام کد درخواست یکتا دارد تا PendingIntentها روی هم نیفتند
        val code = (msgId * 2 + salt).toInt()
        return PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun smsManager(subId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) {
            val base = getSystemService(SmsManager::class.java)
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            @Suppress("DEPRECATION")
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "ارسال پیامک", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("در حال ارسال پیامک‌ها…")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIF_ID, n)
    }

    companion object {
        const val EXTRA_REQUEST = "request"
        const val EXTRA_MESSAGE = "message"
        const val ACTION_SENT = "ir.goldpayamak.app.SMS_SENT"
        const val ACTION_DELIVERED = "ir.goldpayamak.app.SMS_DELIVERED"
        private const val CHANNEL = "send"
        private const val NOTIF_ID = 17

        /** تعداد درخواست‌هایی که همین حالا در حال ارسال‌اند (برای تشخیص پیام‌های رهاشده) */
        val active = java.util.concurrent.atomic.AtomicInteger(0)

        fun start(ctx: Context, requestId: Long) {
            val i = Intent(ctx, SendService::class.java).putExtra(EXTRA_REQUEST, requestId)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
