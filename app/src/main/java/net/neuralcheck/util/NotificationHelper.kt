package net.neuralcheck.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import net.neuralcheck.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SCAN = "scan_progress"
        const val CHANNEL_SAFETY = "safety_stop"
        const val CHANNEL_SYNC = "sync_status"
        // Launch-flavor channel for subscription / payment confirmations. Kept
        // at DEFAULT importance so the user sees the tray entry without the
        // heads-up banner the SAFETY channel uses — payment submission is
        // confirmation, not an emergency.
        const val CHANNEL_ACCOUNT = "account_events"
        // FCM push channel. HIGH importance so the heads-up banner surfaces
        // time-sensitive messages (payment approved, subscription expiring,
        // etc.) even when the user is inside a different app.
        const val CHANNEL_PUSH = "remote_push"
        const val SCAN_NOTIFICATION_ID = 1001
        const val SAFETY_NOTIFICATION_ID = 1002
        const val SYNC_NOTIFICATION_ID = 1003
        const val ACCOUNT_NOTIFICATION_ID = 1004
        // Base id for push messages. We increment per message so successive
        // pushes stack in the tray instead of overwriting each other.
        private const val PUSH_NOTIFICATION_ID_BASE = 2000
    }

    private var nextPushId = PUSH_NOTIFICATION_ID_BASE

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = try {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        } catch (t: Throwable) {
            android.util.Log.w("NeuralCheck", "NotificationManager unavailable: ${t.message}")
            null
        } ?: return
        // Each channel creation is independent — a failure on one (e.g. a
        // missing string resource on a stripped APK) shouldn't drop the
        // others. createNotificationChannel itself is idempotent so we
        // can safely re-call on every onCreate.
        fun channel(id: String, name: String, importance: Int, desc: String? = null) = try {
            nm.createNotificationChannel(NotificationChannel(id, name, importance).apply {
                if (desc != null) description = desc
            })
        } catch (t: Throwable) {
            android.util.Log.w("NeuralCheck", "createNotificationChannel($id) failed: ${t.message}")
        }
        channel(CHANNEL_SCAN, context.getString(R.string.notification_channel_scan),
            NotificationManager.IMPORTANCE_LOW, "Scan progress notifications")
        channel(CHANNEL_SAFETY, "Safety Stop", NotificationManager.IMPORTANCE_HIGH)
        channel(CHANNEL_SYNC, "Sync Status", NotificationManager.IMPORTANCE_MIN)
        channel(CHANNEL_ACCOUNT, context.getString(R.string.notification_channel_account),
            NotificationManager.IMPORTANCE_DEFAULT, "Account and payment events")
        channel(CHANNEL_PUSH, context.getString(R.string.notification_channel_push),
            NotificationManager.IMPORTANCE_HIGH, "Remote push notifications")
    }

    fun showScanProgress(phase: Int, progressPercent: Int): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt())
            .setContentTitle(context.getString(R.string.notification_scan_title))
            .setContentText(context.getString(R.string.notification_scan_progress, phase, progressPercent))
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun showSafetyStop(reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SAFETY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt())
            .setContentTitle(context.getString(R.string.notification_safety_stop))
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SAFETY_NOTIFICATION_ID, notification)
    }

    fun showSyncComplete(count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt())
            .setContentTitle(context.getString(R.string.notification_sync_complete))
            .setContentText(context.getString(R.string.notification_sync_complete_count, count))
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SYNC_NOTIFICATION_ID, notification)
    }

    /**
     * Fires when the user just submitted a ShamCash reference — serves as a
     * persistent reminder that back-office review is in progress. Separate
     * from the in-app snackbar ([InAppNotifier]) because that only lives
     * while the app is in the foreground; this channel keeps the status
     * discoverable when the user backgrounds the app before activation lands.
     */
    fun showPaymentSubmitted() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ACCOUNT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt())
            .setContentTitle(context.getString(R.string.notification_payment_submitted_title))
            .setContentText(context.getString(R.string.notification_payment_submitted_body))
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ACCOUNT_NOTIFICATION_ID, notification)
    }

    /**
     * Displays an FCM-delivered push. Called from
     * [net.neuralcheck.service.NeuralCheckMessagingService.onMessageReceived]
     * with either the notification payload (title/body) or the data payload.
     * Each message gets a unique id so the tray stacks pushes instead of
     * collapsing them onto a single entry.
     */
    fun showRemoteMessage(title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PUSH)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(nextPushId++, notification)
    }
}
