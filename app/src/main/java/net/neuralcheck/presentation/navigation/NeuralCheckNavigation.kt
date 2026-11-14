package net.neuralcheck.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.auth.AuthKind
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.presentation.screen.*

@Composable
fun NeuralCheckNavHost(navController: NavHostController) {
    // Launch flavor routes unauthenticated users to LoginScreen; once signed
    // in the nav graph falls through to the regular home flow. The
    // datacollect flavor never requires auth, so it always starts at home.
    // A-SUB wave 1 (user decision 1, P1): a device whose only user is an ANONYMOUS one (a customer's phone that joined by code) is not an account - it starts at login too,
    // where the code-entry button is (AnonymousRouting.kt). The kind is read through AuthManager.kindOf, the one reading of FirebaseUser.isAnonymous.
    val startDestination = startDestinationFor(BuildConfig.REQUIRE_SUBSCRIPTION, AuthManager.kindOf(Firebase.auth.currentUser))

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onJoinSession = {
                    // Customer device flow: jumps straight to the code-entry
                    // screen without logging in. JoinSessionScreen stores
                    // the resolved sessionId so the ensuing scan can upload
                    // without Firebase Auth.
                    navController.navigate("join_session")
                }
            )
        }

        composable("onboarding") {
            OnboardingScreen(onComplete = {
                navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }

        composable("home") {
            ClosedToAnonymous(navController, authKindNow()) {
                HomeScreen(navController)
            }
        }

        composable("qr_scanner") {
            QRScannerScreen(navController)
        }

        composable(
            route = "pre_scan/{scanType}/{linkedScanId}",
            arguments = listOf(
                navArgument("scanType") { type = NavType.StringType },
                navArgument("linkedScanId") { type = NavType.StringType; defaultValue = "none" }
            )
        ) { backStackEntry ->
            val scanType = backStackEntry.arguments?.getString("scanType") ?: "general"
            val linkedScanId = backStackEntry.arguments?.getString("linkedScanId")?.takeIf { it != "none" }
            PreScanScreen(navController, scanType, linkedScanId)
        }

        composable(
            route = "active_scan/{scanType}/{linkedScanId}",
            arguments = listOf(
                navArgument("scanType") { type = NavType.StringType },
                navArgument("linkedScanId") { type = NavType.StringType; defaultValue = "none" }
            )
        ) { backStackEntry ->
            val scanType = backStackEntry.arguments?.getString("scanType") ?: "general"
            val linkedScanId = backStackEntry.arguments?.getString("linkedScanId")?.takeIf { it != "none" }
            ActiveScanScreen(navController, scanType, linkedScanId)
        }

        composable(
            route = "manual_tests/{scanId}",
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            ManualTestsScreen(navController, scanId)
        }

        composable(
            route = "report/{scanId}",
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            ReportScreen(navController, scanId)
        }

        composable(
            route = "tech_pre_repair/{scanId}",
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            TechPreRepairScreen(navController, scanId)
        }

        composable(
            route = "tech_post_repair/{scanId}",
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            TechPostRepairScreen(navController, scanId)
        }

        composable(
            route = "comparison/{scanId1}/{scanId2}",
            arguments = listOf(
                navArgument("scanId1") { type = NavType.StringType },
                navArgument("scanId2") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scanId1 = backStackEntry.arguments?.getString("scanId1") ?: ""
            val scanId2 = backStackEntry.arguments?.getString("scanId2") ?: ""
            ComparisonScreen(navController, scanId1, scanId2)
        }

        composable(
            route = "peer_survey/{scanId}",
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            PeerSurveyScreen(navController, scanId)
        }

        composable("history") {
            ClosedToAnonymous(navController, authKindNow()) {
                HistoryScreen(navController)
            }
        }

        composable(
            route = "history_select/{scanType}",
            arguments = listOf(navArgument("scanType") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanType = backStackEntry.arguments?.getString("scanType") ?: "post_repair"
            ClosedToAnonymous(navController, authKindNow()) {
                HistoryScreen(navController, selectForScanType = scanType)
            }
        }

        composable("settings") {
            ClosedToAnonymous(navController, authKindNow()) {
                SettingsScreen(navController)
            }
        }

        // ---- Launch-flavor account / subscription flow ---------------------
        // These routes compile in both flavors (the screens themselves don't
        // branch on BuildConfig), but they're only reachable from the launch
        // flavor's Home profile icon + Profile action list. Kept unconditional
        // here so deep links, tests, and manual navController.navigate() calls
        // behave the same regardless of flavor.

        composable("profile") {
            ClosedToAnonymous(navController, authKindNow()) {
                ProfileScreen(navController)
            }
        }

        composable("subscription") {
            ClosedToAnonymous(navController, authKindNow()) {
                SubscriptionScreen(navController)
            }
        }

        // Shop-owner storefront editor. Compiles in both flavors (the
        // screen itself works against the client SDK with no flavor
        // gating), but in practice the entry point in ProfileScreen is
        // shown only to shop_basic / shop_pro subscribers.
        composable("shop_setup") {
            ClosedToAnonymous(navController, authKindNow()) {
                ShopSetupScreen(navController)
            }
        }

        composable(
            route = "payment/{plan}",
            arguments = listOf(navArgument("plan") { type = NavType.StringType })
        ) { backStackEntry ->
            // Default "single" if the nav arg goes missing for any reason;
            // the Cloud Functions backend validates the plan key anyway, so
            // a bad value gets rejected server-side rather than crashing here.
            val plan = backStackEntry.arguments?.getString("plan") ?: "single"
            ClosedToAnonymous(navController, authKindNow()) {
                PaymentScreen(navController, plan)
            }
        }

        composable("scan_history") {
            ClosedToAnonymous(navController, authKindNow()) {
                ScanHistoryScreen(navController)
            }
        }

        composable("payment_history") {
            ClosedToAnonymous(navController, authKindNow()) {
                PaymentHistoryScreen(navController)
            }
        }

        // ---- Cross-device scan session flow --------------------------------
        // Owner (shop-side) generates a session + QR; customer device joins
        // via the code. Both routes reachable from non-authenticated users
        // in their respective entry points (HomeScreen for owner, LoginScreen
        // for customer) so we don't gate them on REQUIRE_SUBSCRIPTION at the
        // nav level.

        composable("generate_session") {
            GenerateSessionScreen(navController)
        }

        composable("join_session") {
            JoinSessionScreen(navController)
        }

        // A-P6-12: the consent screen, between the join and the pre-scan screen. It is reached only through afterJoinRoute (only while CONSENT_TEXTS_APPROVED is true) - the route is
        // registered either way, so the screen is one call away the day the texts are approved.
        composable(CONSENT_ROUTE) {
            ConsentScreen(navController)
        }
    }
}

/**
 * The kind of identity this device has when the entry is composed (A-SUB wave 1): read at each guarded entry, not once at the graph's creation, so an entry built after a sign-in or
 * a sign-out sees the current user. The one reading is `AuthManager.kindOf`; the datacollect flavor has no account flow and nothing is closed there (`effectiveAuthKind`).
 */
private fun authKindNow(): AuthKind =
    effectiveAuthKind(BuildConfig.REQUIRE_SUBSCRIPTION, AuthManager.kindOf(Firebase.auth.currentUser))
