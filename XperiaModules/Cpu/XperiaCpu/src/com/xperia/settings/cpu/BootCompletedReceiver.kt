/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.cpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "boot action=${intent.action}")
        try {
            CpuService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "start CpuService failed", e)
        }
    }

    companion object {
        private const val TAG = "XperiaCpu"
    }
}
