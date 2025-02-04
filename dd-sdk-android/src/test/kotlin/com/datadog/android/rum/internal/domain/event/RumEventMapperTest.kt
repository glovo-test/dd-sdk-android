/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import android.util.Log
import com.datadog.android.event.EventMapper
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.EventType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
internal class RumEventMapperTest {
    lateinit var testedRumEventMapper: RumEventMapper

    @Mock
    lateinit var mockResourceEventMapper: EventMapper<ResourceEvent>

    @Mock
    lateinit var mockActionEventMapper: EventMapper<ActionEvent>

    @Mock
    lateinit var mockErrorEventMapper: EventMapper<ErrorEvent>

    @Mock
    lateinit var mockViewEventMapper: EventMapper<ViewEvent>

    @Mock
    lateinit var mockLongTaskEventMapper: EventMapper<LongTaskEvent>

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockSdkLogHandler: LogHandler

    lateinit var mockDevLogHandler: LogHandler

    lateinit var originalLogHandler: LogHandler

    @Forgery
    lateinit var fakeRumEvent: RumEvent

    @BeforeEach
    fun `set up`() {
        originalLogHandler = mockSdkLogHandler(mockSdkLogHandler)
        mockDevLogHandler = mockDevLogHandler()

        GlobalRum.monitor = mockRumMonitor
        GlobalRum.isRegistered.set(true)

        whenever(mockViewEventMapper.map(any())).thenAnswer { it.arguments[0] }

        testedRumEventMapper = RumEventMapper(
            actionEventMapper = mockActionEventMapper,
            viewEventMapper = mockViewEventMapper,
            resourceEventMapper = mockResourceEventMapper,
            errorEventMapper = mockErrorEventMapper,
            longTaskEventMapper = mockLongTaskEventMapper
        )
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalLogHandler)

        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `M map the bundled event W map { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ViewEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockViewEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ResourceEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockResourceEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ErrorEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockErrorEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ActionEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockActionEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<LongTaskEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockLongTaskEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M return the original event W map { no internal mapper used }`() {
        // GIVEN
        testedRumEventMapper = RumEventMapper()
        val bundledEvent = fakeRumEvent.event

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(bundledEvent)
    }

    @Test
    fun `M return the original event W map { bundled event unknown }`() {
        // GIVEN
        testedRumEventMapper = RumEventMapper()
        val fakeRumEventCopy = fakeRumEvent.copy(event = Any())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEventCopy)

        // THEN
        verify(mockSdkLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.NO_EVENT_MAPPER_ASSIGNED_WARNING_MESSAGE
                .format(Locale.US, fakeRumEventCopy.event.javaClass.simpleName)
        )
        assertThat(mappedRumEvent).isEqualTo(fakeRumEventCopy)
    }

    @Test
    fun `M use the original event W map returns null object { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ViewEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockViewEventMapper.map(fakeBundledEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns null object { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ResourceEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockResourceEventMapper.map(fakeBundledEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns null object { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ErrorEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockErrorEventMapper.map(fakeBundledEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns null object { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ActionEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockActionEventMapper.map(fakeBundledEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns null object { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<LongTaskEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockLongTaskEventMapper.map(fakeBundledEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M use the original event W map returns different object { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ViewEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockViewEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns different object { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ResourceEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockResourceEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns different object { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ErrorEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockErrorEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns different object { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ActionEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockActionEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns different object { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<LongTaskEvent>()
        fakeRumEvent = fakeRumEvent.copy(event = fakeBundledEvent)
        whenever(mockLongTaskEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {action dropped}`(
        @Forgery actionEvent: ActionEvent
    ) {
        // Given
        fakeRumEvent = fakeRumEvent.copy(event = actionEvent)
        whenever(mockActionEventMapper.map(actionEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockRumMonitor).eventDropped(actionEvent.view.id, EventType.ACTION)
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {resource dropped}`(
        @Forgery resourceEvent: ResourceEvent
    ) {
        // Given
        fakeRumEvent = fakeRumEvent.copy(event = resourceEvent)
        whenever(mockResourceEventMapper.map(resourceEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockRumMonitor).eventDropped(resourceEvent.view.id, EventType.RESOURCE)
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {error dropped}`(
        @Forgery errorEvent: ErrorEvent
    ) {
        // Given
        fakeRumEvent = fakeRumEvent.copy(event = errorEvent)
        whenever(mockErrorEventMapper.map(errorEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockRumMonitor).eventDropped(errorEvent.view.id, EventType.ERROR)
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {longTask dropped}`(
        @Forgery longTaskEvent: LongTaskEvent
    ) {
        // Given
        fakeRumEvent = fakeRumEvent.copy(event = longTaskEvent)
        // whenever(mockLongTaskEventMapper.map(longTaskEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockRumMonitor).eventDropped(longTaskEvent.view.id, EventType.LONG_TASK)
    }
}
