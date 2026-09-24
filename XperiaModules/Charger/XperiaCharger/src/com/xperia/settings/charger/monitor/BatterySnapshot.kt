/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

/**
 * Snapshot realtime pin.
 * currentUa: microampere tho tu kernel/BM.
 * voltageUv: microvolt.
 * chargeCounterUa / chargeFullUa: uAh (neu kernel cung cap).
 * powerNowUw: microwatt tu power_now, Long.MIN_VALUE neu khong co.
 */
data class BatterySnapshot(
    val levelPercent: Int,
    val temperatureTenthC: Int,
    val currentUa: Long,
    val voltageUv: Long,
    val pluggedType: Int,
    val status: Int,
    val charging: Boolean,
    val chargeCounterUa: Long = -1L,
    val chargeFullUa: Long = -1L,
    val powerNowUw: Long = Long.MIN_VALUE
) {
    val temperatureC: Float
        get() = temperatureTenthC / 10f

    /**
     * Dong hien thi (mA): duong khi nap, am khi xa.
     * Chuan hoa theo trang thai cam sac (khong tin tuyet doi dau raw).
     */
    val signedCurrentMa: Float
        get() {
            if (currentUa == 0L) return 0f
            val absMa = kotlin.math.abs(currentUa) / 1000f
            return if (charging) absMa else -absMa
        }

    /** Cong suat W: uu tien power_now, fallback I*V. */
    val signedPowerW: Float
        get() {
            if (powerNowUw != Long.MIN_VALUE && powerNowUw != 0L) {
                val absW = kotlin.math.abs(powerNowUw) / 1_000_000f
                return if (charging) absW else -absW
            }
            if (voltageUv <= 0L || currentUa == 0L) return 0f
            val v = voltageUv / 1_000_000.0
            return (v * signedCurrentMa / 1000.0).toFloat()
        }

    /** Muc pin phan so 0..100 tu charge_counter, fallback levelPercent. */
    val preciseCapacityPercent: Float
        get() {
            if (chargeCounterUa > 0L && chargeFullUa > 0L) {
                return ((chargeCounterUa.toDouble() / chargeFullUa.toDouble()) * 100.0)
                    .toFloat()
                    .coerceIn(0f, 100f)
            }
            return if (levelPercent in 0..100) levelPercent.toFloat() else -1f
        }
}
