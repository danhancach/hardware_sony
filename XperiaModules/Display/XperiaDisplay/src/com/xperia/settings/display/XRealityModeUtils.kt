/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.Context
import android.hardware.display.ColorDisplayManager
import android.util.Log

import vendor.semc.hardware.display.V2_0.IDisplay

/**
 * X-Reality Pro — tyty path (Evolution 10/8 XperiaDisplay):
 *   enabled:  sspp=2, ColorDisplayManager BOOSTED(1), set_color_mode(1)
 *   disabled: sspp=1, ColorDisplayManager AUTOMATIC(3), set_color_mode(1)
 *
 * XR must not register the SEMC display callback. Creator owns the PCC callback path.
 */
class XRealityModeUtils(private val context: Context) {
    private var initialized = false

    private val colorDisplayManager: ColorDisplayManager =
            context.getSystemService(ColorDisplayManager::class.java)
                    ?: throw Exception("Display manager is NULL")
    private val semcDisplayService: IDisplay by lazy {
        val service = IDisplay.getService() ?: throw Exception("SEMC Display HIDL not found")
        service.setup()
        service
    }

    val isEnabled: Boolean
        get() = isEnabledInSettings(context)

    fun setMode(enabled: Boolean): Boolean {
        return try {
            semcDisplayService.set_sspp_color_mode(
                if (enabled) SSPP_MODE_EXTENSION_A else SSPP_MODE_STANDARD
            )
            colorDisplayManager.setColorMode(
                if (enabled) COLOR_MODE_BOOSTED else COLOR_MODE_AUTOMATIC
            )
            semcDisplayService.set_color_mode(CGAMUT_MODE_STANDARD)

            DisplayModeSettings.setXRealityEnabled(context, enabled)
            Log.i(TAG, "X-Reality Mode enabled=$enabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set X-Reality Mode enabled=$enabled", e)
            false
        }
    }

    fun ensureInitialized() {
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        initialize()
    }

    fun initialize() {
        Log.i(TAG, "XReality Mode controller setup")
        try {
            if (isEnabled) {
                setMode(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize X-Reality Mode", e)
        }
    }

    companion object {
        private const val TAG = "XRealityUtils"
        const val XREALITY_MODE_ENABLE = "xr_enable"
        const val SWITCH_XREALITY_MODE = "switchXRealityMode"

        private const val CGAMUT_MODE_STANDARD = 1
        private const val SSPP_MODE_STANDARD = 1
        private const val SSPP_MODE_EXTENSION_A = 2
        private const val COLOR_MODE_BOOSTED = 1
        private const val COLOR_MODE_AUTOMATIC = 3

        fun isEnabledInSettings(context: Context): Boolean {
            return DisplayModeSettings.isXRealityEnabled(context)
        }

        fun resetInitialization() {
            // No singleton; DisplayModeInitializer creates a fresh instance after APK update.
        }
    }
}
