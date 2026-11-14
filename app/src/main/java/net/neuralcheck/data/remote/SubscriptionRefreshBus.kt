package net.neuralcheck.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide signal that the user's subscription state may have changed
 * server-side. Producers (FCM push handler when a `payment_approved` data
 * message lands; ProfileViewModel after a successful promo redeem; etc.)
 * call [emit] to publish a refresh tick. Consumers (HomeViewModel for the
 * dashboard tiles, ProfileViewModel for the stats card, PreScanViewModel
 * for the free-trial banner) collect [events] and re-run their own
 * `checkSubscription` / `loadProfile` call.
 *
 * Why a SharedFlow instead of a callback list:
 *  - Multiple ViewModels need the signal, scoped to different lifetimes.
 *    A SharedFlow + viewModelScope auto-cancels collectors when their VM
 *    clears, so we don't have to track listeners by hand.
 *  - `replay = 0` so a new collector doesn't get a stale tick — the only
 *    valid moment to refresh is "right after the bus emits", and any newly-
 *    minted VM already runs its own first-load anyway.
 *  - `extraBufferCapacity = 4` covers the (rare) burst case of the FCM push
 *    arriving while the user is mid-promo-redeem on the same device.
 */
@Singleton
class SubscriptionRefreshBus @Inject constructor() {

    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun emit() {
        _events.tryEmit(Unit)
    }
}
