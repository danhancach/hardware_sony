/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

import android.content.Context
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import java.io.RandomAccessFile

/**
 * Doc thong so pin: intent sticky + sysfs (current/voltage/charge_counter).
 * Sysfs dung RandomAccessFile de giam open/close khi poll UI.
 */
object BatteryReader {

    private const val SYSFS_BATTERY = "/sys/class/power_supply/battery"
    private const val PATH_CURRENT = "$SYSFS_BATTERY/current_now"
    private const val PATH_VOLTAGE = "$SYSFS_BATTERY/voltage_now"
    private const val PATH_CHARGE_COUNTER = "$SYSFS_BATTERY/charge_counter"
    private const val PATH_CHARGE_FULL = "$SYSFS_BATTERY/charge_full"
    private const val PATH_POWER_NOW = "$SYSFS_BATTERY/power_now"

    @Volatile
    private var chargeFullCacheUa: Long = -1L

    fun read(context: Context): BatterySnapshot? {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        return fromIntent(context, intent)
    }

    fun fromIntent(context: Context, intent: Intent): BatterySnapshot {
        val bm = context.getSystemService(BatteryManager::class.java)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val levelPercent = if (level >= 0) (level * 100) / scale else -1

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        // Uu tien plugged: status co the cham/sai khi vua rut sac
        val charging = when {
            plugged != 0 -> true
            status == BatteryManager.BATTERY_STATUS_DISCHARGING -> false
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            status == BatteryManager.BATTERY_STATUS_CHARGING -> true
            status == BatteryManager.BATTERY_STATUS_FULL -> plugged != 0
            else -> false
        }

        var currentUa = readSysfsLong(PATH_CURRENT)
        if (currentUa == null) {
            val prop = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                ?: Long.MIN_VALUE
            currentUa = if (prop == Long.MIN_VALUE || prop == Long.MAX_VALUE) 0L else prop
        }

        var voltageUv = readSysfsLong(PATH_VOLTAGE)
        if (voltageUv == null || voltageUv <= 0L) {
            var fromIntent = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).toLong()
            if (fromIntent in 1..100_000) fromIntent *= 1000L
            voltageUv = fromIntent.coerceAtLeast(0L)
        }

        var chargeCounter = readSysfsLong(PATH_CHARGE_COUNTER)
        if (chargeCounter == null || chargeCounter <= 0L) {
            val prop = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                ?: Long.MIN_VALUE
            if (prop > 0L && prop != Long.MAX_VALUE) chargeCounter = prop
        }

        var chargeFull = chargeFullCacheUa
        if (chargeFull <= 0L) {
            chargeFull = readSysfsLong(PATH_CHARGE_FULL) ?: -1L
            if (chargeFull > 0L) chargeFullCacheUa = chargeFull
        }

        // power_now (uW) neu co — chinh xac hon I*V khi kernel cung cap
        val powerUw = readSysfsLong(PATH_POWER_NOW)

        return BatterySnapshot(
            levelPercent = levelPercent,
            temperatureTenthC = temp,
            currentUa = currentUa ?: 0L,
            voltageUv = voltageUv ?: 0L,
            pluggedType = plugged,
            status = status,
            charging = charging,
            chargeCounterUa = chargeCounter ?: -1L,
            chargeFullUa = chargeFull,
            powerNowUw = powerUw ?: Long.MIN_VALUE
        )
    }

    /** Chi doc current/voltage — nhe hon khi UI poll. */
    fun readCurrentVoltage(): Pair<Long, Long> {
        val current = readSysfsLong(PATH_CURRENT) ?: 0L
        val voltage = readSysfsLong(PATH_VOLTAGE) ?: 0L
        return current to voltage
    }

    private fun readSysfsLong(path: String): Long? {
        return try {
            RandomAccessFile(path, "r").use { raf ->
                raf.seek(0)
                raf.readLine()?.trim()?.toLongOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }
}
