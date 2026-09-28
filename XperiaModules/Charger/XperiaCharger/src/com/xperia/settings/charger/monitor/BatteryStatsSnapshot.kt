/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

/**
 * Thong ke tich luy tu luc dat lai / bat dau.
 * Drain percent: tong % pin da hao (chi khi xa, khong dem luc sac).
 */
data class BatteryStatsSnapshot(
    val totalOnBatteryMs: Long,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val screenOnDrainPercent: Float,
    val screenOffDrainPercent: Float,
    val deepSleepMs: Long,
    val heldAwakeMs: Long,
    val chargeMs: Long
) {
    val screenOnDrainPerHour: Float
        get() = BatteryStatsLogic.drainPerHour(screenOnDrainPercent, screenOnMs)

    val screenOffDrainPerHour: Float
        get() = BatteryStatsLogic.drainPerHour(screenOffDrainPercent, screenOffMs)

    /** Ty le giu danh thuc trong thoi gian man hinh tat (0..100). */
    val heldAwakePercentOfScreenOff: Float
        get() = if (screenOffMs <= 0L) 0f else (heldAwakeMs * 100f) / screenOffMs

    companion object {
        val EMPTY = BatteryStatsSnapshot(0, 0, 0, 0f, 0f, 0, 0, 0)
    }
}
