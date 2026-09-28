/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

import android.content.Context
import android.provider.Settings

/**
 * Cong tac chinh giam sat pin (Settings.Global).
 * Mac dinh bat — giu hanh vi cu truoc khi co switch.
 */
object BatteryMonitorPrefs {

    const val ENABLE_KEY = "xperia_battery_monitor_enable"
    const val SELECTED_RANGE_KEY = "xperia_battery_monitor_selected_range"

    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, ENABLE_KEY, 1) == 1

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Global.putInt(context.contentResolver, ENABLE_KEY, if (enabled) 1 else 0)
        if (!enabled) {
            BatteryMonitorNotification.cancel(context)
        }
    }

    /** Khoang thong ke UI — luu Global; SINCE_UNPLUG khong persist (map ve START). */
    fun getSelectedRange(context: Context): StatsRange {
        val ord = Settings.Global.getInt(
            context.contentResolver,
            SELECTED_RANGE_KEY,
            StatsRange.SINCE_START.ordinal
        )
        return StatsRange.entries.getOrElse(ord) { StatsRange.SINCE_START }.asUiRange()
    }

    fun setSelectedRange(context: Context, range: StatsRange) {
        Settings.Global.putInt(
            context.contentResolver,
            SELECTED_RANGE_KEY,
            range.asUiRange().ordinal
        )
    }
}
