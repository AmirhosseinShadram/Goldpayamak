package ir.goldpayamak.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.goldpayamak.app.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** نتیجهٔ ارسال و تحویل هر بخش از پیامک را در دیتابیس ثبت می‌کند. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SendService.EXTRA_MESSAGE, -1L)
        if (id < 0) return
        val ok = resultCode == Activity.RESULT_OK
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(context).dao()
                val t = System.currentTimeMillis()
                when (action) {
                    SendService.ACTION_SENT -> if (ok) dao.incSent(id, t) else dao.incFailed(id, t)
                    SendService.ACTION_DELIVERED -> if (ok) dao.incDelivered(id, t)
                }
                dao.recomputeStatus(id)
            } finally {
                pending.finish()
            }
        }
    }
}
