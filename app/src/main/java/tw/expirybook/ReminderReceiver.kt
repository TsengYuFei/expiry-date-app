package tw.expirybook

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.Executors

object Reminders {
    private val worker = Executors.newSingleThreadExecutor()
    fun refresh(context: Context, done: () -> Unit = {}) {
        worker.execute { try { synchronized(this) { reconcile(context.applicationContext) } } finally { done() } }
    }
    private fun reconcile(context: Context) {
        Store(context).use { store ->
            val manager = context.getSystemService(NotificationManager::class.java)
            if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("expiry","有效期限提醒",NotificationManager.IMPORTANCE_DEFAULT))
            val alarm = context.getSystemService(AlarmManager::class.java)
            val pending = PendingIntent.getBroadcast(context,0,Intent(context,ReminderReceiver::class.java).setAction("tw.expirybook.REMIND"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(pending)
            if(!store.enabled) { manager.cancelAll(); return }
            val items=store.items()
            items.filter { !it.reminder || it.status != 0 }.forEach { manager.cancel(it.id.toInt()) }
            val eligible=items.filter { it.reminder && it.status==0 && it.delivered!=Rules.token(it) }
            val allowed=(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
                manager.areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT<26 || manager.getNotificationChannel("expiry").importance!=NotificationManager.IMPORTANCE_NONE)
            if(!allowed) return
            val now=System.currentTimeMillis()
            eligible.filter { Rules.due(it)<=now }.forEach { item ->
                val open=PendingIntent.getActivity(context,item.id.toInt(),Intent(context,MainActivity::class.java).putExtra("item",item.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val builder=if(Build.VERSION.SDK_INT>=26) Notification.Builder(context,"expiry") else @Suppress("DEPRECATION") Notification.Builder(context)
                manager.notify(item.id.toInt(),builder.setSmallIcon(R.drawable.ic_book).setContentTitle("${item.name}即將過期")
                    .setContentText("${Rules.roc(item.expiry)}・${Rules.expiryText(item.expiry)}").setContentIntent(open).setAutoCancel(true).build())
                store.delivered(item)
            }
            eligible.map { Rules.due(it) }.filter { it>now }.minOrNull()?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,it,pending) }
        }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action !in setOf("tw.expirybook.REMIND",Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending=goAsync()
        Reminders.refresh(context) { pending.finish() }
    }
}
