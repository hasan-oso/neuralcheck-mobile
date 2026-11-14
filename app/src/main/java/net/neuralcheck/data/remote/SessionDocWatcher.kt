package net.neuralcheck.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import net.neuralcheck.util.EvalCardLog

/**
 * Reads ONE session document, `scanSessions/{id}`, live — batch 2 (order 2026-10-15). Used where a device must follow a session's status: the
 * customer device, to learn that the owner cancelled (decision D5), and the owner's own screen (item 3).
 *
 * What it does and does not do:
 *  - It turns a Firestore snapshot into a [Snapshot] whose `status` and `cancelReason` are READ, not guessed: [SessionStatus.Read] and
 *    [CancelReason.Read] each say known, unknown (a text the app has no case for - printed, never mapped onto a known value) or absent (rules 22 and 25). A
 *    document that does not exist is [Snapshot.docExists] = false, its own named state, not an empty status.
 *  - It prints one `session-watch-event` line per snapshot (booleans, named states and a hashed id only), one `session-watch-error` line when the read
 *    ends in an error, and one `session-watch-stop` line when it is removed.
 *  - It reads; it never writes the document (the rules allow no client write, `firestore.rules:140-145`, relayed in the order).
 *  - It does not start, bind or keep alive any service: it is a Firestore listener and nothing else. Whoever holds the [Registration] removes it.
 *
 * Who may read: the owner, or a device whose uid equals the document's `joinedBy` (`firestore.rules:141-144`, relayed). A device that joined with no identity
 * (`joinedBy` null) cannot: its listener ends with `permission-denied`, reported through [watch]'s `onError`.
 *
 * Offline: the SDK keeps the listener and delivers the current document when the connection returns; nothing is delivered meanwhile, so a cancellation made
 * while the device was offline is seen at reconnection.
 *
 * The Firestore instance is looked up at each [watch] ([firestore] is a lambda) so a test can substitute the static `getInstance()` boundary.
 */
class SessionDocWatcher(private val firestore: () -> FirebaseFirestore = { Firebase.firestore }) {

    /** One snapshot of the document. [scanId] is the `scanId` the server wrote on completion (a text), else null. */
    data class Snapshot(
        val docExists: Boolean,
        val status: SessionStatus.Read,
        val cancelReason: CancelReason.Read,
        val scanId: String?
    )

    /** Ends the read. Calling it twice is harmless. */
    fun interface Registration {
        fun remove()
    }

    /**
     * Starts reading `scanSessions/[sessionId]`. [onSnapshot] gets every snapshot; [onError] the error that ended the read (the SDK ends a listener on an error);
     * both are called on the thread Firestore uses (the main thread). [scanId], when given, only labels the `session-watch-start` line.
     */
    fun watch(
        sessionId: String,
        scanId: String? = null,
        onSnapshot: (Snapshot) -> Unit,
        onError: (Throwable) -> Unit
    ): Registration {
        EvalCardLog.sessionWatchStart(scanId, sessionId)
        val removed = java.util.concurrent.atomic.AtomicBoolean(false)
        val registration = firestore().collection(COLLECTION).document(sessionId).addSnapshotListener { snap, err ->
            if (removed.get()) return@addSnapshotListener
            if (err != null) {
                EvalCardLog.sessionWatchError(sessionId, errorToken(err))
                onError(err)
                return@addSnapshotListener
            }
            if (snap == null) {
                // the SDK's contract is "a snapshot or an error"; both missing is a named failure, not an empty document
                EvalCardLog.sessionWatchError(sessionId, "no-snapshot-no-error")
                onError(IllegalStateException("the listener delivered neither a snapshot nor an error"))
                return@addSnapshotListener
            }
            val read = read(snap)
            EvalCardLog.sessionWatchEvent(
                sessionId = sessionId,
                doc = if (read.docExists) "present" else "missing",
                status = statusToken(read.status),
                statusRaw = (read.status as? SessionStatus.Read.Unknown)?.raw,
                cancelReason = cancelReasonToken(read.cancelReason),
                cancelReasonRaw = (read.cancelReason as? CancelReason.Read.Unknown)?.raw
            )
            onSnapshot(read)
        }
        return Registration {
            if (removed.compareAndSet(false, true)) {
                registration.remove()
                EvalCardLog.sessionWatchStop(sessionId, "removed")
            }
        }
    }

    companion object {
        /** The collection of the session documents: the one place the name is written. */
        const val COLLECTION = "scanSessions"

        /** Reads a snapshot with `get` (any type), never `getString` (which throws on a field of another type): a non-text field is absent, not a crash. */
        internal fun read(snap: DocumentSnapshot): Snapshot {
            val exists = snap.exists()
            if (!exists) return Snapshot(false, SessionStatus.Read.Absent, CancelReason.Read.Absent, null)
            return Snapshot(
                docExists = true,
                status = SessionStatus.read(snap.get("status")),
                cancelReason = CancelReason.read(snap.get("cancelReason")),
                scanId = snap.get("scanId") as? String
            )
        }

        internal fun statusToken(read: SessionStatus.Read): String = when (read) {
            is SessionStatus.Read.Known -> read.status.wire
            is SessionStatus.Read.Unknown -> "unknown"
            SessionStatus.Read.Absent -> "absent"
        }

        internal fun cancelReasonToken(read: CancelReason.Read): String = when (read) {
            is CancelReason.Read.Known -> read.reason.wire
            is CancelReason.Read.Unknown -> "unknown"
            CancelReason.Read.Absent -> "absent"
        }

        /** The Firestore error code as a lower-case token (`permission-denied`), or `other` for anything that is not a Firestore error. */
        internal fun errorToken(err: Throwable): String =
            (err as? FirebaseFirestoreException)?.code?.name?.lowercase()?.replace('_', '-') ?: "other"
    }
}
