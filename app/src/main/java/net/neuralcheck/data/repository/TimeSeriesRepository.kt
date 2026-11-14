package net.neuralcheck.data.repository

import android.content.Context
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface TimeSeriesRepository {
    fun getWriter(): TimeSeriesFileWriter
    fun getReader(): TimeSeriesFileReader
    suspend fun deleteTimeSeries(scanId: String): Boolean
    fun getTimeSeriesPath(scanId: String): String?
}

@Singleton
class TimeSeriesRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : TimeSeriesRepository {

    private val writer = TimeSeriesFileWriter(context)
    private val reader = TimeSeriesFileReader(context)

    override fun getWriter(): TimeSeriesFileWriter = writer

    override fun getReader(): TimeSeriesFileReader = reader

    override suspend fun deleteTimeSeries(scanId: String): Boolean {
        return reader.deleteFile(scanId)
    }

    override fun getTimeSeriesPath(scanId: String): String? {
        return reader.getFilePath(scanId)
    }
}
