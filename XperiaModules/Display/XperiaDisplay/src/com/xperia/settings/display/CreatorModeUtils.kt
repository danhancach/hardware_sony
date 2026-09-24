/*
 * Copyright (C) 2023-2024 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.app.ActivityTaskManager
import android.content.Context
import android.hardware.display.ColorDisplayManager
import android.os.RemoteException
import android.util.Log
import vendor.semc.hardware.display.V2_0.IDisplay
import vendor.semc.hardware.display.V2_0.IDisplayCallback
import vendor.semc.hardware.display.V2_0.PccMatrix

import com.xperia.settings.display.server.DisplayTransformManager

/**
 * Creator Mode — matches working tyty path (Evolution 10/8 XperiaDisplay):
 *   sspp=0, ColorDisplayManager NATURAL(0), set_color_mode(0)
 * Do not call DisplayTransformManager.setColorMode — it forces SF ENHANCED/MANAGED and breaks creator.
 */
class CreatorModeUtils private constructor(private val context: Context) : IDisplayCallback.Stub() {
    private var initialized = false

    private val colorDisplayManager: ColorDisplayManager =
            context.getSystemService(ColorDisplayManager::class.java)
                    ?: throw Exception("Display manager is NULL")
    private val semcDisplayService: IDisplay by lazy { openDisplayService() }

    private fun openDisplayService(): IDisplay {
        var last: Exception? = null
        for (attempt in 1..8) {
            try {
                val service = IDisplay.getService()
                    ?: throw Exception("SEMC Display HIDL getService() returned null")
                service.setup()
                return service
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "Display HAL not ready (attempt $attempt)", e)
                Thread.sleep(250)
            }
        }
        throw last ?: Exception("SEMC Display HIDL not found")
    }

    private val dtm: DisplayTransformManager = DisplayTransformManager()

    val isEnabled: Boolean
        get() = DisplayModeSettings.isCreatorEnabled(context)

    fun setMode(enabled: Boolean): Boolean {
        if (enabled) {
            ensureInitialized()
        }
        return try {
            semcDisplayService.set_sspp_color_mode(
                if (enabled) SSPP_MODE_CREATOR else SSPP_MODE_STANDARD
            )
            colorDisplayManager.setColorMode(
                if (enabled) COLOR_MODE_NATURAL else COLOR_MODE_AUTOMATIC
            )
            semcDisplayService.set_color_mode(
                if (enabled) CGAMUT_MODE_CREATOR else CGAMUT_MODE_STANDARD
            )

            DisplayModeSettings.setCreatorEnabled(context, enabled)

            Log.i(TAG, "Creator Mode enabled=$enabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Creator Mode enabled=$enabled", e)
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
        Log.i(TAG, "Creator Mode controller setup")
        try {
            if (!isEnabled) {
                semcDisplayService.set_sspp_color_mode(SSPP_MODE_STANDARD)
                colorDisplayManager.setColorMode(COLOR_MODE_AUTOMATIC)
                semcDisplayService.set_color_mode(CGAMUT_MODE_STANDARD)
            }
            semcDisplayService.registerCallback(this)
            if (isEnabled) {
                setMode(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Creator Mode", e)
        }
    }

    override fun onWhiteBalanceMatrixChanged(matrix: PccMatrix) {
        val r = matrix.red
        val g = matrix.green
        val b = matrix.blue
        try {
            dtm.setColorMatrix(
                LEVEL_COLOR_MATRIX_CREATOR_MODE,
                floatArrayOf(
                    r, 0f, 0f, 0f,
                    0f, g, 0f, 0f,
                    0f, 0f, b, 0f,
                    0f, 0f, 0f, 1f
                )
            )
            updateConfiguration()
            Log.i(TAG, "New white balance: $r, $g, $b")
        } catch (e: Exception) {
            Log.e(TAG, "Could not apply setColorMatrix", e)
        }
    }

    fun updateConfiguration() {
        try {
            ActivityTaskManager.getService().updateConfiguration(null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not update configuration", e)
        }
    }

    companion object {
        private const val TAG = "CreatorModeUtils"
        const val CREATOR_MODE_ENABLE = DisplayModeSettings.CREATOR_ENABLE
        const val SWITCH_CREATOR_MODE = DisplayModeSettings.CREATOR_SWITCH

        private const val CGAMUT_MODE_CREATOR = 0
        private const val CGAMUT_MODE_STANDARD = 1
        private const val SSPP_MODE_CREATOR = 0
        private const val SSPP_MODE_STANDARD = 1
        private const val COLOR_MODE_NATURAL = 0
        private const val COLOR_MODE_AUTOMATIC = 3
        private const val LEVEL_COLOR_MATRIX_CREATOR_MODE = 5690

        @Volatile
        private var instance: CreatorModeUtils? = null

        fun resetInitialization() {
            instance = null
        }

        fun get(context: Context): CreatorModeUtils {
            val appContext = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: CreatorModeUtils(appContext).also { instance = it }
            }
        }
    }
}
