/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "APK updated, reinitializing display modes")
        DisplayModeInitializer.reset()
        DisplayModeInitializer.ensureInitialized(context)
    }

    companion object {
        private const val TAG = "XperiaDisplay"
    }
}
