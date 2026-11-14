package net.neuralcheck.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.neuralcheck.data.local.db.DestructiveMigrationCallback
import net.neuralcheck.data.local.db.NeuralCheckDatabase
import net.neuralcheck.data.local.db.dao.*
import net.neuralcheck.data.local.prefs.DestructiveMigrationTracker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDestructiveMigrationTracker(
        @ApplicationContext context: Context
    ): DestructiveMigrationTracker = DestructiveMigrationTracker(
        context.getSharedPreferences(DestructiveMigrationTracker.PREFS_NAME, Context.MODE_PRIVATE)
    )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        destructiveMigrationTracker: DestructiveMigrationTracker
    ): NeuralCheckDatabase {
        return Room.databaseBuilder(
            context,
            NeuralCheckDatabase::class.java,
            NeuralCheckDatabase.DATABASE_NAME
        )
            // Real migrations preserve existing scans + scores across
            // schema bumps. v1→v2 added scan_scores.shortId; v2→v3 added
            // scans.userId so the home / history queries can filter by the
            // signed-in account on a multi-account device.
            //
            // The list lives in NeuralCheckDatabase.ALL_MIGRATIONS (one place:
            // 1→2 … 7→8, each documented on its own constant) and
            // NeuralCheckDatabaseMigrationChainTest proves it is contiguous up
            // to SCHEMA_VERSION, so fallbackToDestructiveMigration() below is
            // never reached for a bump on a technician device with saved
            // reports. v7→v8 (fieldsVersion 68, order 2026-10-08):
            // functional_results.measurements.
            .addMigrations(*NeuralCheckDatabase.ALL_MIGRATIONS.toTypedArray())
            // Safety net for any future schema bumps that ship without a
            // matching migration during development.
            .fallbackToDestructiveMigration()
            // Order 2026-09-24، القرار ٨٠ البند ١: the fallback above stays,
            // but is no longer silent — this records the wipe locally (no
            // network call) so the UI can show the user a one-time notice.
            // See DestructiveMigrationCallback / DestructiveMigrationTracker.
            .addCallback(DestructiveMigrationCallback(destructiveMigrationTracker))
            .build()
    }

    @Provides
    fun provideScanDao(database: NeuralCheckDatabase): ScanDao = database.scanDao()

    @Provides
    fun provideFunctionalResultDao(database: NeuralCheckDatabase): FunctionalResultDao = database.functionalResultDao()

    @Provides
    fun provideTechnicianInputDao(database: NeuralCheckDatabase): TechnicianInputDao = database.technicianInputDao()

    @Provides
    fun providePeerInputDao(database: NeuralCheckDatabase): PeerInputDao = database.peerInputDao()

    @Provides
    fun provideScanScoreDao(database: NeuralCheckDatabase): ScanScoreDao = database.scanScoreDao()

    @Provides
    fun provideSessionEvaluationDao(database: NeuralCheckDatabase): SessionEvaluationDao =
        database.sessionEvaluationDao()
}
