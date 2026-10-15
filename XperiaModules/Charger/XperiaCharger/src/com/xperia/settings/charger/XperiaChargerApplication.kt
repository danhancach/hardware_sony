/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.charger

import android.app.Application
import com.google.android.material.color.DynamicColors

class XperiaChargerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ap dung Dynamic Color cho toan bo activity trong app
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
