/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.app.Application

class XperiaDisplayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DisplayModeInitializer.ensureInitialized(this)
    }
}
