package net.neuralcheck.data.auth

import com.google.firebase.auth.FirebaseUser
import net.neuralcheck.presentation.viewmodel.RealJoinPathHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * How the device's identity reads - A-SUB wave 1, user decision 1 «P1»: `AuthManager.isAnonymous`, the one reading of `FirebaseUser.isAnonymous`, for the three states: an anonymous user, an
 * account, and no user. Rule 22: no user is neither anonymous nor an account (not read as either). Rule 37: the second test goes through the REAL [AuthManager] and the harness's REAL path
 * (`joinScanSessionWithDeviceIdentity` signs the phone in anonymously through the real `signInAnonymously` call) - a stand-in user that reports `isAnonymous = false` is what a test that
 * "replaced the layer" would have built.
 */
class AuthKindTest {

    private fun user(anonymous: Boolean): FirebaseUser = mock { on { isAnonymous } doReturn anonymous }

    @Test
    fun `the kind of a user is anonymous, registered or none - and a missing user is none`() {
        assertEquals(AuthKind.ANONYMOUS, AuthManager.kindOf(user(anonymous = true)))
        assertEquals(AuthKind.REGISTERED, AuthManager.kindOf(user(anonymous = false)))
        assertEquals(AuthKind.NONE, AuthManager.kindOf(null))
    }

    @Test
    fun `the real AuthManager reads no user, an anonymous user and an account, as the device is signed in and out`() {
        RealJoinPathHarness(signedInAtStart = false).use { h ->
            assertEquals(AuthKind.NONE, h.authManager.authKind)
            assertFalse("no user is not anonymous", h.authManager.isAnonymous)
            assertFalse(h.authManager.isLoggedIn)

            // the real sign-in the join and the analysis make
            kotlinx.coroutines.runBlocking { h.authManager.signInAnonymously() }
            assertEquals(AuthKind.ANONYMOUS, h.authManager.authKind)
            assertTrue(h.authManager.isAnonymous)
            assertTrue("an anonymous user still counts as a user (isLoggedIn is unchanged: the engine and the join read it)", h.authManager.isLoggedIn)

            h.signInAs(h.registeredUser)
            assertEquals(AuthKind.REGISTERED, h.authManager.authKind)
            assertFalse("an account is not anonymous", h.authManager.isAnonymous)
            assertTrue(h.authManager.isLoggedIn)

            h.signInAs(null)
            assertEquals(AuthKind.NONE, h.authManager.authKind)
            assertNull(h.authManager.currentUser)
        }
    }
}
