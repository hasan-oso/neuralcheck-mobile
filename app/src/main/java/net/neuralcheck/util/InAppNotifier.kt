package net.neuralcheck.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide pipe for transient in-app messages (snackbars) that don't fit
 * the system notification tray. The launch-flavor payment flow, for example,
 * pushes "receipt received — review in progress" here so the user sees the
 * confirmation immediately, even before the Firebase tray notification fires.
 *
 * One-shot SharedFlow (replay = 0) because every message is ephemeral — a
 * late subscriber (e.g. a newly composed SnackbarHost) shouldn't redisplay a
 * snackbar from minutes ago. Buffer = 16 + DROP_OLDEST so a burst of events
 * while the UI is temporarily detached (config change) doesn't deadlock the
 * emitter.
 *
 * Consumers are expected to be a Compose Scaffold at the top of the nav
 * graph; see [net.neuralcheck.presentation.MainActivity] for the single
 * subscription that turns these into Material3 Snackbars.
 */
@Singleton
class InAppNotifier @Inject constructor() {

    private val _events = MutableSharedFlow<AppNotification>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AppNotification> = _events.asSharedFlow()

    /**
     * Pushes a localized message. Messages are resolved against the active
     * locale at *display* time (the Compose layer reads `stringResource`),
     * so callers pass the `@StringRes` id rather than a pre-resolved string.
     */
    fun push(messageResId: Int, severity: Severity = Severity.INFO) {
        _events.tryEmit(AppNotification(messageResId, severity))
    }

    data class AppNotification(
        val messageResId: Int,
        val severity: Severity
    )

    enum class Severity { INFO, SUCCESS, WARNING, ERROR }
}
