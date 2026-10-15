/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.audio

import android.app.Application
import com.google.android.material.color.DynamicColors

class XperiaAudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ap dung Dynamic Color cho toan bo activity trong app
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
