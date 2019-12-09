/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.constraints

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.log.forge.Configurator
import com.datadog.android.utils.extension.SystemOutStream
import com.datadog.android.utils.extension.SystemOutputExtension
import com.datadog.android.utils.lastLine
import com.datadog.android.utils.setStaticValue
import com.datadog.android.utils.times
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class DatadogLogConstraintsTest {

    lateinit var testedConstraints: LogConstraints

    @BeforeEach
    fun `set up`() {
        // we need to set the Build.MODEL to null, to override the setup
        Build::class.java.setStaticValue("MODEL", null)

        testedConstraints = DatadogLogConstraints()
    }

    // region Tags

    @Test
    fun `keep valid tag`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val tag = forge.aStringMatching("[a-z]([a-z0-9_:./-]{0,198}[a-z0-9_./-])?")

        val result = testedConstraints.validateTags(listOf(tag))

        assertThat(result)
            .containsOnly(tag)
        assertThat(outputStream.lastLine())
            .isNull()
    }

    @Test
    fun `ignore invalid tag - start with a letter`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val key = forge.aStringMatching("\\d[a-z]+")
        val value = forge.aNumericalString()
        val tag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        assertThat(result)
            .isEmpty()
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "E/DD_LOG: DatadogLogConstraints: \"$tag\" is an invalid tag, " +
                        "and was ignored."
                )
        }
    }

    @Test
    fun `replace illegal characters`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val validPart = forge.anAlphabeticalString(size = 3)
        val invalidPart = forge.aString {
            anElementFrom(',', '?', '%', '(', ')', '[', ']', '{', '}')
        }
        val value = forge.aNumericalString()
        val tag = "$validPart$invalidPart:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        val converted = '_' * invalidPart.length
        val expectedTag = "$validPart$converted:$value"
        assertThat(result)
            .containsOnly(expectedTag)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: tag \"$tag\" " +
                        "was modified to \"$expectedTag\" to match our constraints."
                )
        }
    }

    @Test
    fun `convert uppercase key to lowercase`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val key = forge.anAlphabeticalString(case = Case.UPPER)
        val value = forge.aNumericalString()
        val tag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        val expectedTag = "${key.toLowerCase(Locale.US)}:$value"
        assertThat(result)
            .containsOnly(expectedTag)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: tag \"$tag\" " +
                        "was modified to \"$expectedTag\" to match our constraints."
                )
        }
    }

    @Test
    fun `trim tags over 200 characters`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val tag = forge.anAlphabeticalString(size = forge.aSmallInt() + 200)

        val result = testedConstraints.validateTags(listOf(tag))

        val expectedTag = tag.substring(0, 200)
        assertThat(result)
            .containsOnly(expectedTag)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: tag \"$tag\" " +
                        "was modified to \"$expectedTag\" to match our constraints."
                )
        }
    }

    @Test
    fun `trim tags ending with a colon`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val tag = forge.anAlphabeticalString()

        val result = testedConstraints.validateTags(listOf("$tag:"))

        assertThat(result)
            .containsOnly(tag)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: tag \"$tag:\" " +
                        "was modified to \"$tag\" to match our constraints."
                )
        }
    }

    @Test
    fun `ignore reserved tag keys`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val key = forge.anElementFrom("host", "device", "source", "service")
        val value = forge.aNumericalString()
        val tag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        assertThat(result)
            .isEmpty()
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "E/DD_LOG: DatadogLogConstraints: \"$tag\" is an invalid tag, " +
                        "and was ignored."
                )
        }
    }

    @Test
    fun `ignore reserved tag keys (workaround)`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val key = forge.randomizeCase { anElementFrom("host", "device", "source", "service") }
        val value = forge.aNumericalString()
        val tag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        assertThat(result)
            .isEmpty()
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "E/DD_LOG: DatadogLogConstraints: \"$tag\" is an invalid tag," +
                        " and was ignored."
                )
        }
    }

    @Test
    fun `ignore tag if adding more than 100`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val tags = forge.aList(128) { aStringMatching("[a-z]{1,8}:[0-9]{1,8}") }
        val firstTags = tags.take(100)

        val result = testedConstraints.validateTags(tags)

        val discardedCount = tags.size - 100
        assertThat(result)
            .containsExactlyElementsOf(firstTags)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: too many tags were added, " +
                        "$discardedCount had to be discarded."
                )
        }
    }

    //endregion

    // region Attributes

    @Test
    fun `keep valid attribute`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()

        val result = testedConstraints.validateAttributes(mapOf(key to value))

        assertThat(result)
            .containsEntry(key, value)
        assertThat(outputStream.lastLine())
            .isNull()
    }

    @Test
    fun `convert nested attribute keys over 10 levels`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val topLevels = forge.aList(10) { anAlphabeticalString() }
        val lowerLevels = forge.aList { anAlphabeticalString() }
        val key = (topLevels + lowerLevels).joinToString(".")
        val value = forge.aNumericalString()

        val result = testedConstraints.validateAttributes(mapOf(key to value))

        val expectedKey = topLevels.joinToString(".") + "_" + lowerLevels.joinToString("_")
        assertThat(result)
            .containsEntry(expectedKey, value)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: attribute \"$key\" " +
                        "was modified to \"$expectedKey\" to match our constraints."
                )
        }
    }

    @Test
    fun `ignore attribute if adding more than 256`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val attributes = forge.aList(300) { anAlphabeticalString() to anInt() }.toMap()
        val firstAttributes = attributes.toList().take(256).toMap()

        val result = testedConstraints.validateAttributes(attributes)

        val discardedCount = attributes.size - 256
        assertThat(result)
            .hasSize(256)
            .containsAllEntriesOf(firstAttributes)
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.lastLine())
                .isEqualTo(
                    "W/DD_LOG: DatadogLogConstraints: too many attributes were added, " +
                        "$discardedCount had to be discarded."
                )
        }
    }

    // endregion
}
