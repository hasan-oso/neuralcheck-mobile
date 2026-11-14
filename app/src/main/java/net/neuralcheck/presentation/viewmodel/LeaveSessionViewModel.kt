package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.neuralcheck.data.remote.SessionLeaver
import javax.inject.Inject

/**
 * The state of the customer's leave (batch 2, item 2, D2), for the two screens that offer it: the pre-scan screen (its leave button and Back) and the scan
 * screen (its stop dialog and Back). The logic is [SessionLeaver]; this holds what a screen shows: nothing happening, the call out, the refusal's TEXT, or
 * done. One view model for both screens, each screen with its own instance (`hiltViewModel()` per back-stack entry).
 *
 * The text of a failure is decided by `SessionMessages.kt` (the server's `details.reasonCode`; the two approved cancel texts; everything else the text that point
 * already showed for a failure, with a `session-refused` line) - [SessionCall.CANCEL_CUSTOMER]. A failure that is «no connection», and `SESSION_EXPIRED` and `SESSION_NOT_PARTICIPANT` (batch 4, item 2), never get here:
 * [SessionLeaver] ends those as a leave ([State.Left]).
 */
@HiltViewModel
class LeaveSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val leaver: SessionLeaver
) : ViewModel() {

    sealed interface State {
        data object Idle : State

        /** The cancellation is out: the confirmation shows progress and its buttons do nothing more. */
        data object InProgress : State

        /** The leave failed; [message] is the text to show. Nothing changed: the session, the holder and the scan are as they were. */
        data class Failed(val message: String) : State

        /**
         * The leave is done and the holder was dealt with: the screen leaves (the scan screen stops its scan first). Done means the server confirmed the
         * cancellation, or answered `SESSION_EXPIRED`, or the call could not reach the server (batch 3, item 1: a local leave), or the server answered `SESSION_NOT_PARTICIPANT` (batch 4, item 2: a local leave) - [SessionLeaver] decides which and prints it.
         */
        data object Left : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** See [SessionLeaver.sessionOfPreScan]. */
    fun sessionOfPreScan(): String? = leaver.sessionOfPreScan()

    /** See [SessionLeaver.sessionOfScan]. */
    fun sessionOfScan(scanId: String): String? = leaver.sessionOfScan(scanId)

    /** Leaves [sessionId] from [phase]; a second call while one is in progress, or after it succeeded, does nothing. */
    fun leave(phase: SessionLeaver.Phase, sessionId: String, scanId: String? = null) {
        if (_state.value is State.InProgress || _state.value is State.Left) return
        _state.value = State.InProgress
        viewModelScope.launch {
            try {
                leaver.leave(phase, sessionId, scanId)
                _state.value = State.Left
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = State.Failed(sessionErrorMessage(context, SessionCall.CANCEL_CUSTOMER, t))
            }
        }
    }

    /** The user closed the confirmation after a failure: back to nothing happening. */
    fun dismiss() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }
}
