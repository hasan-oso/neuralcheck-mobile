package net.neuralcheck.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.local.prefs.SecurePreferences
import net.neuralcheck.domain.scoring.AiScoringEngine
import net.neuralcheck.domain.scoring.RuleBasedScoringEngine
import net.neuralcheck.domain.scoring.ScoringConfigReader
import net.neuralcheck.domain.scoring.ScoringEngine
import net.neuralcheck.util.ComparisonReportGenerator
import net.neuralcheck.util.NotificationHelper
import net.neuralcheck.util.ReportImageGenerator
import javax.inject.Singleton

/**
 * Hilt module for engine-level dependencies: utilities, report generators,
 * notification helpers, and encrypted preferences.
 *
 * Hardware test classes (BatteryTests, CpuTests, etc.) use @Inject constructor
 * and are resolved automatically by Hilt via constructor injection.
 *
 * ScoringEngine will be provided in Phase 08 (RuleBasedScoringEngine).
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideScoringConfigReader(@ApplicationContext context: Context): ScoringConfigReader =
        ScoringConfigReader(context)

    // ScoringEngine is flavor-conditional:
    //   - launch (USE_AI_SCORING=true):   AiScoringEngine. Builds a compact
    //                                     behaviour fingerprint and ships it
    //                                     to the `analyzeDevice` Firebase
    //                                     Callable Cloud Function. The launch
    //                                     report has no rule-based fallback —
    //                                     a network failure surfaces as an
    //                                     error card with a retry affordance.
    //   - datacollect (USE_AI_SCORING=false): RuleBasedScoringEngine only, so
    //                                         internal test sessions compare
    //                                         against a deterministic baseline
    //                                         with no external dependencies.
    //
    // Both engines are provided as @Singleton concrete types elsewhere
    // (RuleBasedScoringEngine via @Inject constructor, AiScoringEngine same);
    // this provider just picks which one binds to the ScoringEngine interface.
    @Provides
    @Singleton
    fun provideScoringEngine(
        ruleBased: RuleBasedScoringEngine,
        aiScoring: dagger.Lazy<AiScoringEngine>
    ): ScoringEngine = if (BuildConfig.USE_AI_SCORING) {
        // `Lazy` because AiScoringEngine transitively requires
        // FirebaseAnalysisService (Firebase Functions callable client). The
        // datacollect flavor never reaches this branch; Lazy defers
        // construction so the Firebase graph is only touched on launch.
        aiScoring.get()
    } else {
        ruleBased
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(@ApplicationContext context: Context): NotificationHelper =
        NotificationHelper(context)

    @Provides
    @Singleton
    fun provideSecurePreferences(@ApplicationContext context: Context): SecurePreferences =
        SecurePreferences(context)

    @Provides
    @Singleton
    fun provideReportImageGenerator(@ApplicationContext context: Context): ReportImageGenerator =
        ReportImageGenerator(context)

    @Provides
    @Singleton
    fun provideComparisonReportGenerator(@ApplicationContext context: Context): ComparisonReportGenerator =
        ComparisonReportGenerator(context)
}
