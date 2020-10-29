/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.batching

import com.datadog.android.core.internal.data.batching.migrators.BatchedDataMigrator
import com.datadog.android.core.internal.data.privacy.Consent

internal interface MigratorFactory {
    fun resolveMigrator(
        prevConsentFlag: Consent?,
        newConsentFlag: Consent
    ): BatchedDataMigrator
}
