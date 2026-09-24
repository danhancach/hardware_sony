/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.Context
import android.util.Log

/** Applies persisted display mode state after boot, APK update, or process restart. */
object DisplayModeInitializer {
    private const val TAG = "DisplayModeInitializer"

    @Volatile
    private var initialized = false
    private val lock = Any()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val appContext = context.applicationContext
            val creatorMode = CreatorModeUtils.get(appContext)
            val xRealityMode = XRealityModeUtils(appContext)

            if (creatorMode.isEnabled && xRealityMode.isEnabled) {
                xRealityMode.setMode(false)
            }

            creatorMode.ensureInitialized()
            xRealityMode.ensureInitialized()
            initialized = true
            Log.i(TAG, "Display modes initialized")
        }
    }

    fun reset() {
        synchronized(lock) {
            initialized = false
            CreatorModeUtils.resetInitialization()
            XRealityModeUtils.resetInitialization()
        }
    }
}
