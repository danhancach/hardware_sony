/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.app.Application
import com.google.android.material.color.DynamicColors

class XperiaDisplayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ap dung Dynamic Color cho toan bo activity
        DynamicColors.applyToActivitiesIfAvailable(this)
        DisplayModeInitializer.ensureInitialized(this)
    }
}

