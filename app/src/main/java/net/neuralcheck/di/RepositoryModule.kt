package net.neuralcheck.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.neuralcheck.data.repository.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository

    @Binds
    @Singleton
    abstract fun bindTestResultRepository(impl: TestResultRepositoryImpl): TestResultRepository

    @Binds
    @Singleton
    abstract fun bindTechnicianRepository(impl: TechnicianRepositoryImpl): TechnicianRepository

    @Binds
    @Singleton
    abstract fun bindPeerRepository(impl: PeerRepositoryImpl): PeerRepository

    @Binds
    @Singleton
    abstract fun bindScoreRepository(impl: ScoreRepositoryImpl): ScoreRepository

    @Binds
    @Singleton
    abstract fun bindTimeSeriesRepository(impl: TimeSeriesRepositoryImpl): TimeSeriesRepository
}
