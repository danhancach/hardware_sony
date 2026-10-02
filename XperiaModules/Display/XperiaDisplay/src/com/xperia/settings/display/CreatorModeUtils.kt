/*
 * Copyright (C) 2023-2024 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.app.ActivityTaskManager
import android.content.Context
import android.hardware.display.ColorDisplayManager
import android.os.DeadObjectException
import android.os.RemoteException
import android.util.Log
import android.view.Display
import vendor.semc.hardware.display.V2_0.IDisplay
import vendor.semc.hardware.display.V2_0.IDisplayCallback
import vendor.semc.hardware.display.V2_0.PccMatrix

import com.xperia.settings.display.server.DisplayTransformManager

/**
 * Creator / Standard - Option B (khop stock DisplayBooster orchestration, khong port APK):
 *   HAL sspp -> ColorDisplayManager + DTM SF Managed/Enhanced -> HAL cgamut (PCC callback)
 * Creator: NATURAL / Managed / COLORIMETRIC + PCC identity
 * Standard: AUTOMATIC / Enhanced / ENHANCE + PCC tu HAL (~0.857/0.903/0.951)
 */
class CreatorModeUtils private constructor(private val context: Context) : IDisplayCallback.Stub() {
    private var initialized = false
    private var callbackRegistered = false

    // true = dang Creator: PCC phai identity; false = Standard: nhan matrix HAL
    @Volatile
    private var expectCreatorPcc: Boolean = false

    private val colorDisplayManager: ColorDisplayManager =
            context.getSystemService(ColorDisplayManager::class.java)
                    ?: throw Exception("Display manager is NULL")
    private val semcDisplayService: IDisplay by lazy { openDisplayService() }

    /**
     * getService -> registerCallback -> setup.
     * setup()/applyPccMatrix goi callback cu neu chua thay stub sau process death
     * -> DEAD_OBJECT + HAL SIGABRT (blob khong check return_status).
     */
    private fun openDisplayService(): IDisplay {
        var last: Exception? = null
        for (attempt in 1..8) {
            try {
                val service = IDisplay.getService()
                    ?: throw Exception("SEMC Display HIDL getService() returned null")
                // Thay stub truoc setup de applyPccMatrix khong goi peer da chet
                service.registerCallback(this)
                callbackRegistered = true
                service.setup()
                return service
            } catch (e: Exception) {
                last = e
                callbackRegistered = false
                val dead = e is DeadObjectException || e.cause is DeadObjectException
                Log.w(
                    TAG,
                    if (dead) {
                        "Display HAL dead/stale callback (attempt $attempt), doi HAL restart"
                    } else {
                        "Display HAL not ready (attempt $attempt)"
                    },
                    e
                )
                // HAL SIGABRT can them thoi gian init restart
                Thread.sleep(if (dead) 500L else 250L)
            }
        }
        throw last ?: Exception("SEMC Display HIDL not found")
    }

    private val dtm: DisplayTransformManager = DisplayTransformManager()

    val isEnabled: Boolean
        get() = DisplayModeSettings.isCreatorEnabled(context)

    fun setMode(enabled: Boolean): Boolean {
        return try {
            // Chi dang ky HAL; khong apply persisted (tranh Standard xen khi bat Creator)
            registerCallbackIfNeeded()
            applyMode(enabled)
            DisplayModeSettings.setCreatorEnabled(context, enabled)
            synchronized(this) { initialized = true }
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
            // Dang ky callback truoc orchestration de PCC HAL khong bi mat
            registerCallbackIfNeeded()
            applyMode(isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Creator Mode", e)
        }
    }

    /**
     * Thu tu khop stock: sspp -> SF/CDM orchestration -> cgamut (kich PCC).
     */
    private fun applyMode(creatorEnabled: Boolean) {
        expectCreatorPcc = creatorEnabled

        semcDisplayService.set_sspp_color_mode(
            if (creatorEnabled) SSPP_MODE_CREATOR else SSPP_MODE_STANDARD
        )

        val colorMode = if (creatorEnabled) COLOR_MODE_NATURAL else COLOR_MODE_AUTOMATIC
        colorDisplayManager.setColorMode(colorMode)
        // Chi txn 1022/1023 - khong goi setColorMode day du (tranh xoa PCC/Night)
        dtm.applyDisplayColorSetting(colorMode, Display.COLOR_MODE_INVALID)

        // Creator: ep identity ngay, tranh matrix Standard stale khi callback cham
        if (creatorEnabled) {
            applyPccRgb(1f, 1f, 1f)
        }

        semcDisplayService.set_color_mode(
            if (creatorEnabled) CGAMUT_MODE_CREATOR else CGAMUT_MODE_STANDARD
        )
    }

    private fun registerCallbackIfNeeded() {
        // Lazy openDisplayService da registerCallback truoc setup
        val service = semcDisplayService
        if (callbackRegistered) return
        service.registerCallback(this)
        callbackRegistered = true
    }

    override fun onWhiteBalanceMatrixChanged(matrix: PccMatrix) {
        val r = matrix.red
        val g = matrix.green
        val b = matrix.blue
        try {
            if (expectCreatorPcc) {
                // Bo qua stale Standard neu callback den tre sau khi da ve Creator
                if (!isNearIdentity(r, g, b)) {
                    Log.w(TAG, "Bo qua PCC stale khi Creator: $r, $g, $b - giu identity")
                }
                applyPccRgb(1f, 1f, 1f)
            } else {
                applyPccRgb(r, g, b)
            }
            Log.i(TAG, "New white balance: $r, $g, $b")
        } catch (e: Exception) {
            Log.e(TAG, "Could not apply setColorMatrix", e)
        }
    }

    private fun applyPccRgb(r: Float, g: Float, b: Float) {
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
    }

    private fun isNearIdentity(r: Float, g: Float, b: Float): Boolean {
        return kotlin.math.abs(r - 1f) < PCC_IDENTITY_EPS
            && kotlin.math.abs(g - 1f) < PCC_IDENTITY_EPS
            && kotlin.math.abs(b - 1f) < PCC_IDENTITY_EPS
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
        private const val PCC_IDENTITY_EPS = 0.02f

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
