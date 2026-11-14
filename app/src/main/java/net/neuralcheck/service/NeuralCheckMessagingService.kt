package net.neuralcheck.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import net.neuralcheck.R
import net.neuralcheck.data.remote.SubscriptionRefreshBus
import net.neuralcheck.util.NotificationHelper
import javax.inject.Inject

/**
 * Receives FCM pushes from the back-office. We rely on pushes for the
 * payment-activation signal in the launch flavor: the user submits a
 * ShamCash reference, goes about their day, and when the admin approves it
 * the server sends a data-only push that triggers the in-tray notification
 * via [NotificationHelper.showRemoteMessage].
 *
 * Two payload shapes are supported because the console composer and the
 * Admin SDK emit different fields:
 *   - `message.notification` — populated when the sender used the Firebase
 *     console "Notification" form. Title/body are pre-localized by the
 *     sender; we display them verbatim.
 *   - `message.data` — populated when the back-office sends via Admin SDK.
 *     We look for `title` and `body` keys, falling back to a generic
 *     localized string so the user always sees *something* actionable.
 *
 * Token refresh is handled in [onNewToken]: we only persist to Firestore
 * when a user is actually signed in; otherwise the token will be saved on
 * the next successful login by [net.neuralcheck.presentation.viewmodel.LoginViewModel].
 * This avoids creating orphan `users/{uid}` docs for users who haven't
 * completed registration yet.
 */
@AndroidEntryPoint
class NeuralCheckMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var subscriptionRefreshBus: SubscriptionRefreshBus

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to persist FCM token for $uid", e)
            }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.notification_push_default_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.notification_push_default_body)
        notificationHelper.showRemoteMessage(title, body)

        // Subscription-state changes from the back-office (payment approval,
        // plan grants from a promo manually pushed by support, etc.) come in
        // as data-only messages with `type` set to one of the values below.
        // Tell every active VM to re-fetch via the refresh bus so the user
        // doesn't have to background + foreground the app to see their new
        // plan land. Tolerant on the type matching: matches both "payment_
        // approved" and a future "subscription_updated" without forcing the
        // back-office to coordinate strings with the app at every change.
        val type = message.data["type"].orEmpty().lowercase()
        if (type == "payment_approved" ||
            type == "subscription_updated" ||
            type == "subscription_changed" ||
            type == "plan_changed"
        ) {
            Log.d(TAG, "FCM type=$type — broadcasting subscription refresh")
            subscriptionRefreshBus.emit()
        }
    }

    companion object {
        private const val TAG = "FcmService"
    }
}
