package net.neuralcheck.data.remote

/**
 * The customer device follows the session its scan STARTED in, and learns that the owner cancelled it — batch 2, item 2, decision D5 (user, 2026-10-15,
 * recommendation D5 adopted: «يقرأ مستند جلسته … فإن صار cancelled أثناء الفحص: يوقف الفحص، ويعرض نصّ SESSION_CANCELLED المعتمد، ثم D3. ولا تغيير
 * في الخادم»). This class is the logic; the screen's view model ([net.neuralcheck.presentation.viewmodel.ActiveScanViewModel]) owns its lifetime and does
 * the stopping and the showing.
 *
 * ── When it starts ──
 * [start] is called once, when the scan's id is first known (the first `Running` state of the scan service: `StressTestService` mints the id before any
 * phase). It reads the session from [ScanSessionRecords] — the session the scan started in, recorded at scan start (order 2026-10-14) — never from the holder
 * as it is later: a scan with no record is a personal scan and nothing starts (the records print why). It does not start at the join, nor on the pre-scan
 * screen: the scan is what a cancellation interrupts.
 *
 * ── When it ends ──
 * [stop] is called when the scan ends (completed, a safety stop, an error), when the scan is stopped by the user, when the view model is cleared, and by
 * this class itself on a cancellation or an error of the read. After the scan the read is gone: it never outlives the scan screen's view model.
 * It starts, binds and keeps alive NO service — it is a Firestore listener ([SessionDocWatcher]) and the foreground scan service is untouched by it
 * (the view model stops the scan service on a cancellation through its own `stopScan`, exactly as the stop button does).
 *
 * ── What it does on a cancellation ──
 * The document's status read as `cancelled` ends the watch and tells the caller ([onCancelledByOwner]), after emptying the holder IF it still holds that session
 * ([ScanSessionHolder.clearIfHolding]: a session joined meanwhile is not touched) and removing the scan's record ([ScanSessionRecords.consume]: the scan will
 * never be analysed). A cancellation this device made itself (`cancelReason` = `left_by_customer` or `consent_declined`: the leave flow's own call arriving back
 * as a snapshot) is NOT "the owner cancelled": it is ignored here, the leave flow ends the scan itself. An unknown or absent `cancelReason` on a cancelled document is
 * treated as the owner's: the session is cancelled whoever did it.
 *
 * ── If the network drops ──
 * The scan does not depend on the read. While offline nothing is delivered; at reconnection Firestore delivers the current document, so a cancellation made
 * meanwhile is seen then. An error that ends the read (for example `permission-denied` on a device that joined without an identity) is printed
 * (`session-watch-error`) and the scan carries on unwatched - no text, no stop.
 */
class CustomerSessionWatch(
    private val watcher: SessionDocWatcher,
    private val sessionHolder: ScanSessionHolder,
    private val records: ScanSessionRecords,
    /** Called once, with the document's `cancelReason` as read, after the holder and the record were dealt with. */
    private val onCancelledByOwner: (CancelReason.Read) -> Unit
) {
    private var registration: SessionDocWatcher.Registration? = null

    /** Whether a read is running now. */
    @Synchronized
    fun isWatching(): Boolean = registration != null

    /**
     * Starts reading the session [scanId] started in. Returns true when a read started; false when the scan has no session (a personal scan), or a read is already
     * running. Call it once per scan: its answer is then also "did this scan start in a session" - the scan view model keeps it for the screen (A-P6-09: a session's scan shows no charts).
     */
    @Synchronized
    fun start(scanId: String): Boolean {
        if (registration != null) return false
        val recorded = records.sessionFor(scanId, ScanSessionRecords.Use.WATCH) ?: return false
        registration = watcher.watch(
            sessionId = recorded.sessionId,
            scanId = scanId,
            onSnapshot = { onSnapshot(recorded.sessionId, scanId, it) },
            onError = { onError() }
        )
        return true
    }

    /** Ends the read, if one runs. Harmless when none does. */
    @Synchronized
    fun stop() {
        registration?.remove()
        registration = null
    }

    private fun onSnapshot(sessionId: String, scanId: String, snapshot: SessionDocWatcher.Snapshot) {
        val status = snapshot.status as? SessionStatus.Read.Known ?: return
        if (status.status != SessionStatus.CANCELLED) return
        val own = (snapshot.cancelReason as? CancelReason.Read.Known)?.reason in
            setOf(CancelReason.LEFT_BY_CUSTOMER, CancelReason.CONSENT_DECLINED)
        if (own) return
        stop()
        sessionHolder.clearIfHolding(sessionId, ScanSessionHolder.ClearReason.CANCELLED_BY_OWNER)
        records.consume(scanId)
        onCancelledByOwner(snapshot.cancelReason)
    }

    private fun onError() {
        // the read is over (the SDK ends a listener on an error); the scan carries on, unwatched
        stop()
    }
}
