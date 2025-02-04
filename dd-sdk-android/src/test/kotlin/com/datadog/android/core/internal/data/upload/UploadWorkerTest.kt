/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.disposeMainLooper
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.prepareMainLooper
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UploadWorkerTest {

    lateinit var testedWorker: Worker

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockLogsStrategy: PersistenceStrategy<Log>

    @Mock
    lateinit var mockTracesStrategy: PersistenceStrategy<DDSpan>

    @Mock
    lateinit var mockCrashReportsStrategy: PersistenceStrategy<Log>

    @Mock
    lateinit var mockRumStrategy: PersistenceStrategy<RumEvent>

    @Mock
    lateinit var mockLogsReader: DataReader

    @Mock
    lateinit var mockTracesReader: DataReader

    @Mock
    lateinit var mockCrashReportsReader: DataReader

    @Mock
    lateinit var mockRumReader: DataReader

    @Mock
    lateinit var mockLogsUploader: DataUploader

    @Mock
    lateinit var mockTracesUploader: DataUploader

    @Mock
    lateinit var mockCrashReportsUploader: DataUploader

    @Mock
    lateinit var mockRumUploader: DataUploader

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    @BeforeEach
    fun `set up`() {
        whenever(mockLogsStrategy.getReader()) doReturn mockLogsReader
        whenever(mockTracesStrategy.getReader()) doReturn mockTracesReader
        whenever(mockCrashReportsStrategy.getReader()) doReturn mockCrashReportsReader
        whenever(mockRumStrategy.getReader()) doReturn mockRumReader

        mockContext = mockContext()
        prepareMainLooper()
        Datadog.initialize(
            mockContext,
            Credentials("CLIENT_TOKEN", "ENVIRONMENT", "", null),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )

        LogsFeature.persistenceStrategy = mockLogsStrategy
        LogsFeature.uploader = mockLogsUploader
        TracesFeature.persistenceStrategy = mockTracesStrategy
        TracesFeature.uploader = mockTracesUploader
        CrashReportsFeature.persistenceStrategy = mockCrashReportsStrategy
        CrashReportsFeature.uploader = mockCrashReportsUploader
        RumFeature.persistenceStrategy = mockRumStrategy
        RumFeature.uploader = mockRumUploader

        testedWorker = UploadWorker(
            mockContext,
            fakeWorkerParameters
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
        disposeMainLooper()
    }

    @Test
    fun `doWork single batch Success`(
        @Forgery logsBatch: Batch,
        @Forgery tracesBatch: Batch,
        @Forgery crashReportsBatch: Batch
    ) {
        whenever(mockLogsReader.lockAndReadNext()).doReturn(logsBatch, null)
        whenever(mockLogsUploader.upload(logsBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockTracesReader.lockAndReadNext()).doReturn(tracesBatch, null)
        whenever(mockTracesUploader.upload(tracesBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockCrashReportsReader.lockAndReadNext()).doReturn(crashReportsBatch, null)
        whenever(mockCrashReportsUploader.upload(crashReportsBatch.data))
            .doReturn(UploadStatus.SUCCESS)

        val result = testedWorker.doWork()

        verify(mockLogsReader).drop(logsBatch)
        verify(mockLogsReader, never()).release(logsBatch)
        verify(mockTracesReader).drop(tracesBatch)
        verify(mockTracesReader, never()).release(tracesBatch)
        verify(mockCrashReportsReader).drop(crashReportsBatch)
        verify(mockCrashReportsReader, never()).release(crashReportsBatch)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork single batch Failure`(
        @Forgery logsBatch: Batch,
        @Forgery tracesBatch: Batch,
        @Forgery crashReportsBatch: Batch,
        forge: Forge
    ) {
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        whenever(mockLogsReader.lockAndReadNext()).doReturn(logsBatch, null)
        whenever(mockLogsUploader.upload(logsBatch.data)) doReturn status
        whenever(mockTracesReader.lockAndReadNext()).doReturn(tracesBatch, null)
        whenever(mockTracesUploader.upload(tracesBatch.data)) doReturn status
        whenever(mockCrashReportsReader.lockAndReadNext()).doReturn(crashReportsBatch, null)
        whenever(mockCrashReportsUploader.upload(crashReportsBatch.data)) doReturn status

        val result = testedWorker.doWork()

        verify(mockLogsReader, never()).drop(logsBatch)
        verify(mockLogsReader).release(logsBatch)
        verify(mockTracesReader, never()).drop(tracesBatch)
        verify(mockTracesReader).release(tracesBatch)
        verify(mockCrashReportsReader, never()).drop(crashReportsBatch)
        verify(mockCrashReportsReader).release(crashReportsBatch)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple logs batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogsReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockLogsUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockLogsReader).drop(it)
            verify(mockLogsReader, never()).release(it)
        }
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockCrashReportsReader, never()).drop(any())
        verify(mockCrashReportsReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple traces batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockTracesReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockTracesUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockTracesReader).drop(it)
            verify(mockTracesReader, never()).release(it)
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockCrashReportsReader, never()).drop(any())
        verify(mockCrashReportsReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple crashReports batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockCrashReportsReader.lockAndReadNext())
            .doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockCrashReportsUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockCrashReportsReader).drop(it)
            verify(mockCrashReportsReader, never()).release(it)
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple logs batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogsReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockLogsUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockLogsUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockLogsReader, never()).drop(it)
                verify(mockLogsReader).release(it)
            } else {
                verify(mockLogsReader).drop(it)
                verify(mockLogsReader, never()).release(it)
            }
        }
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockCrashReportsReader, never()).drop(any())
        verify(mockCrashReportsReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple traces batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockTracesReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockTracesUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockTracesUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockTracesReader, never()).drop(it)
                verify(mockTracesReader).release(it)
            } else {
                verify(mockTracesReader).drop(it)
                verify(mockTracesReader, never()).release(it)
            }
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockCrashReportsReader, never()).drop(any())
        verify(mockCrashReportsReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple crashReports batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockCrashReportsReader.lockAndReadNext())
            .doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockCrashReportsUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockCrashReportsUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockCrashReportsReader, never()).drop(it)
                verify(mockCrashReportsReader).release(it)
            } else {
                verify(mockCrashReportsReader).drop(it)
                verify(mockCrashReportsReader, never()).release(it)
            }
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }
}
