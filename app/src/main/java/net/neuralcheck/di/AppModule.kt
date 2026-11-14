package net.neuralcheck.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.service.collector.SystemFileReader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSystemFileReader(@ApplicationContext context: Context): SystemFileReader =
        SystemFileReader(context)

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    /** Order 2026-10-14: the per-scan record of the session a scan started in (see [ScanSessionRecords]). */
    @Provides
    @Singleton
    fun provideScanSessionRecords(
        @ApplicationContext context: Context,
        holder: ScanSessionHolder
    ): ScanSessionRecords = ScanSessionRecords(
        context.getSharedPreferences(ScanSessionRecords.PREFS_NAME, Context.MODE_PRIVATE),
        holder
    )
}
