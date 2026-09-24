/*
 * Copyright (C) 2025 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "boot action=$action")
        val utils = ChargerUtils(context)

        // Uu tien ap dung limit/HSPC + start service (truoc unlock)
        try {
            utils.applyOnBoot()
        } catch (e: Exception) {
            Log.e(TAG, "applyOnBoot failed", e)
        }
        try {
            BatteryMonitorService.startService(context)
        } catch (e: Exception) {
            Log.e(TAG, "startService failed", e)
        }

        // Migrate / tat Lineage — khong de crash chan boot path
        try {
            utils.migrateFromLineageChargingControl()
            utils.disableLineageChargingControl()
        } catch (e: Exception) {
            Log.w(TAG, "Lineage migrate/disable skipped ($action)", e)
        }
    }

    companion object {
        private const val TAG = "XperiaCharger"
    }
}
