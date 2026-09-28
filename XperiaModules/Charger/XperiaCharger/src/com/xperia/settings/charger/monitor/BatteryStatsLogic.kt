/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

/** Logic thuan tinh drain/%/h — tach de host unit test, tranh spike. */
object BatteryStatsLogic {

    const val MIN_DRAIN_DURATION_MS = 30_000L
    /** Tran %/h hien thi — tranh spike gauge/calibration. */
    const val MAX_DRAIN_PERCENT_PER_HOUR = 50f

    /**
     * % pin hao trong khoang mau. Uu tien charge_counter; caller dam bao !pluggedBefore.
     */
    fun computeDrainPercent(
        lastLevel: Int,
        levelPercent: Int,
        lastPrecise: Float,
        precise: Float,
        lastCounter: Long,
        counter: Long,
        chargeFull: Long,
        largeGap: Boolean,
        dtElapsed: Long
    ): Float {
        if (largeGap || dtElapsed <= 0L) return 0f

        val maxPct = maxDrainForInterval(dtElapsed)

        if (counter > 0L && lastCounter > 0L && chargeFull > 0L) {
            if (counter > lastCounter) {
                // Counter tang khi xa — calibration / sai mau
                return 0f
            }
            if (counter < lastCounter) {
                val delta = (lastCounter - counter).toDouble()
                val pct = ((delta / chargeFull.toDouble()) * 100.0).toFloat()
                return pct.coerceIn(0f, maxPct)
            }
        }

        if (precise >= 0f && lastPrecise >= 0f && precise < lastPrecise) {
            return (lastPrecise - precise).coerceIn(0f, maxPct)
        }

        if (lastLevel in 0..100 && levelPercent in 0..100 && levelPercent < lastLevel) {
            return (lastLevel - levelPercent).toFloat().coerceIn(0f, maxPct)
        }

        return 0f
    }

    /** Tran % hao cho khoang dt (tuong duong MAX_DRAIN_PERCENT_PER_HOUR). */
    fun maxDrainForInterval(dtElapsed: Long): Float {
        val hours = dtElapsed / 3_600_000f
        if (hours <= 0f) return 0.5f
        return (MAX_DRAIN_PERCENT_PER_HOUR * hours).coerceAtLeast(0.05f)
    }

    fun drainPerHour(drainPercent: Float, durationMs: Long): Float {
        if (durationMs < MIN_DRAIN_DURATION_MS || drainPercent <= 0f) return 0f
        val hours = durationMs / 3_600_000f
        if (hours <= 0f) return 0f
        return (drainPercent / hours).coerceIn(0f, MAX_DRAIN_PERCENT_PER_HOUR)
    }
}
