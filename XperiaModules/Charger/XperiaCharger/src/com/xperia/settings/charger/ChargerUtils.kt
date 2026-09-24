/*
 * Copyright (C) 2025 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar

class ChargerUtils(private val context: Context) {

    var chargingLimit: Int
        get() = Settings.Global.getInt(context.contentResolver, CHARGER_LIMIT_ENABLE, 100)
        set(value) {
            Settings.Global.putInt(context.contentResolver, CHARGER_LIMIT_ENABLE, value.coerceIn(50, 100))
            Log.i(TAG, "Charging limit updated to: $value")
        }

    var chargingMode: Int
        get() = Settings.Global.getInt(context.contentResolver, CHARGER_MODE, MODE_LIMIT)
        set(value) {
            Settings.Global.putInt(context.contentResolver, CHARGER_MODE, value)
            Log.i(TAG, "Charging mode updated to: $value")
        }

    var startTimeSeconds: Int
        get() = Settings.Global.getInt(context.contentResolver, CHARGER_START_TIME, DEFAULT_START_TIME)
        set(value) {
            Settings.Global.putInt(context.contentResolver, CHARGER_START_TIME, value.coerceIn(0, 24 * 60 * 60 - 1))
        }

    var targetTimeSeconds: Int
        get() = Settings.Global.getInt(context.contentResolver, CHARGER_TARGET_TIME, DEFAULT_TARGET_TIME)
        set(value) {
            Settings.Global.putInt(context.contentResolver, CHARGER_TARGET_TIME, value.coerceIn(0, 24 * 60 * 60 - 1))
        }

    /**
     * H.S. Power — sticky qua Settings.Global (cung key GameSpace).
     * GameSpace chi ghi Global; BatteryMonitorService ContentObserver ap sysfs.
     */
    var isHSPCEnabled: Boolean
        get() = Settings.Global.getInt(context.contentResolver, CHARGER_HS_ENABLE, 0) > 0
        set(value) {
            Settings.Global.putInt(context.contentResolver, CHARGER_HS_ENABLE, if (value) 1 else 0)
            val nodeValue = if (value) "1" else "0"
            if (writeSysfs(chargingInterruptionNode, nodeValue)) {
                Log.i(TAG, "HSPC toggled: $nodeValue")
            }
            if (!value) {
                // Re-apply charging rules after HSPC off
                refreshFromBatteryIntent()
            }
        }

    var mainSwitch: Boolean
        get() = Settings.Global.getInt(context.contentResolver, CHARGER_MAIN_ENABLE, 0) > 0
        set(value) {
            Settings.Global.putInt(
                context.contentResolver,
                CHARGER_MAIN_ENABLE,
                if (value) 1 else 0
            )
            if (!value && !isHSPCEnabled) {
                writeSysfs(chargingInterruptionNode, "0")
            } else {
                refreshFromBatteryIntent()
            }
        }

    private val chargingInterruptionNode: String by lazy {
        determineChargingInterruptionNode()
    }

    private fun determineChargingInterruptionNode(): String {
        val board = getSystemProperty("ro.board.platform")
            ?: throw IllegalStateException("Failed to determine board platform")
        val boardIndex = getBoardIndex(board)
        if (boardIndex == -1) {
            throw IllegalArgumentException("Unsupported board detected: $board")
        }
        val boardPath = CHG_INTERRUPTION_PATHS[boardIndex]
        if (File(boardPath).exists()) {
            Log.i(TAG, "Charging interruption node: $boardPath")
            return boardPath
        }
        throw IllegalStateException("No valid charging node found for board: $board")
    }

    private fun getBoardIndex(board: String): Int {
        return when (board) {
            "kona" -> 0
            "lahaina" -> 1
            "taro" -> 2
            "kalama" -> 3
            "lagoon" -> 4
            else -> -1
        }
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            get.invoke(null, key) as String?
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get system property: $key", e)
            null
        }
    }

    fun refreshFromBatteryIntent() {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        if (level != -1) {
            updateChargingState(level)
        }
    }

    /**
     * Ap dung quy tac quan ly sac / HSPC len sysfs.
     * HSPC luon uu tien. Limit/lich chi ghi khi mainSwitch bat.
     */
    fun updateChargingState(currentBatteryLevel: Int) {
        if (isHSPCEnabled) {
            writeSysfs(chargingInterruptionNode, "1")
            Log.i(TAG, "HSPC active: charging interrupted (level=$currentBatteryLevel)")
            return
        }

        if (!mainSwitch) {
            return
        }

        val shouldCharge = when (chargingMode) {
            MODE_LIMIT -> currentBatteryLevel < chargingLimit
            MODE_AUTO, MODE_MANUAL -> shouldChargeForSchedule(currentBatteryLevel)
            else -> currentBatteryLevel < chargingLimit
        }

        val nodeValue = if (shouldCharge) "0" else "1"
        if (writeSysfs(chargingInterruptionNode, nodeValue)) {
            Log.i(
                TAG,
                "mode=$chargingMode level=$currentBatteryLevel limit=$chargingLimit " +
                    "charge=${if (shouldCharge) "on" else "off"}"
            )
        }
    }

    private fun shouldChargeForSchedule(currentBatteryLevel: Int): Boolean {
        val window = getChargeWindow() ?: return true
        val now = System.currentTimeMillis()

        return when {
            now < window.startMs -> {
                // Truoc cua so: cho phep sac toi nguong giu ~80%
                currentBatteryLevel < SCHEDULE_HOLD_LEVEL
            }
            now in window.startMs until window.targetMs -> {
                // Trong cua so: sac day
                currentBatteryLevel < 100
            }
            else -> {
                // Sau gio muc tieu: khong con kiem soat lich
                true
            }
        }
    }

    data class ChargeWindow(val startMs: Long, val targetMs: Long)

    fun getChargeWindow(): ChargeWindow? {
        val now = System.currentTimeMillis()
        return when (chargingMode) {
            MODE_AUTO -> {
                val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return null
                val info = alarmManager.nextAlarmClock ?: return null
                val targetTime = info.triggerTime
                val startTime = targetTime - DateUtils.HOUR_IN_MILLIS * 9
                ChargeWindow(startTime, targetTime)
            }
            MODE_MANUAL -> {
                var startTime = millisFromSecondOfDay(startTimeSeconds)
                var targetTime = millisFromSecondOfDay(targetTimeSeconds)
                if (startTime > targetTime) {
                    if (now > targetTime) {
                        targetTime += DateUtils.DAY_IN_MILLIS
                    } else {
                        startTime -= DateUtils.DAY_IN_MILLIS
                    }
                } else if (now >= targetTime) {
                    startTime += DateUtils.DAY_IN_MILLIS
                    targetTime += DateUtils.DAY_IN_MILLIS
                }
                ChargeWindow(startTime, targetTime)
            }
            else -> null
        }
    }

    private fun millisFromSecondOfDay(secondOfDay: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis + secondOfDay * 1000L
    }

    fun applyOnBoot() {
        // HSPC khong giu qua reboot (GameSpace se bat lai neu auto-HSPC)
        Settings.Global.putInt(context.contentResolver, CHARGER_HS_ENABLE, 0)
        refreshFromBatteryIntent()
    }

    /**
     * Migrate Lineage Charging control -> XperiaCharger (mot lan).
     * Dung Settings.Global (DE) — an toan truoc khi mo khoa man hinh.
     */
    fun migrateFromLineageChargingControl() {
        if (Settings.Global.getInt(context.contentResolver, CHARGER_MIGRATED, 0) != 0) {
            return
        }

        val enabled = readLineageSystemInt("charging_control_enabled", 0)
        val existingMode = Settings.Global.getInt(context.contentResolver, CHARGER_MODE, -1)
        if (enabled == 1 || existingMode < 0) {
            if (enabled == 1) {
                val mode = readLineageSystemInt("charging_control_mode", MODE_LIMIT)
                val limit = readLineageSystemInt("charging_control_charging_limit", 80)
                val start = readLineageSystemInt("charging_control_start_time", DEFAULT_START_TIME)
                val target = readLineageSystemInt("charging_control_target_time", DEFAULT_TARGET_TIME)

                mainSwitch = true
                chargingMode = when (mode) {
                    MODE_AUTO, MODE_MANUAL, MODE_LIMIT -> mode
                    else -> MODE_LIMIT
                }
                chargingLimit = limit.coerceIn(50, 100)
                startTimeSeconds = start
                targetTimeSeconds = target
                Log.i(TAG, "Migrated Lineage CC: mode=$mode limit=$limit")
            } else if (existingMode < 0) {
                // Lan dau: dat mode mac dinh LIMIT neu chua co
                chargingMode = MODE_LIMIT
            }
        }

        writeLineageSystemInt("charging_control_enabled", 0)
        Settings.Global.putInt(context.contentResolver, CHARGER_MIGRATED, 1)
    }

    fun disableLineageChargingControl() {
        writeLineageSystemInt("charging_control_enabled", 0)
    }

    private fun readLineageSystemInt(name: String, default: Int): Int {
        val uri = Uri.parse("content://lineagesettings/system")
        return try {
            context.contentResolver.query(
                uri, arrayOf("value"), "name=?", arrayOf(name), null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.toIntOrNull() ?: default
                } else default
            } ?: default
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read LineageSettings $name", e)
            default
        }
    }

    private fun writeLineageSystemInt(name: String, value: Int) {
        try {
            val args = android.os.Bundle().apply {
                putString("value", value.toString())
                putInt("_user", 0)
            }
            context.contentResolver.call(
                Uri.parse("content://lineagesettings"),
                "PUT_system",
                name,
                args
            )
            Log.i(TAG, "Wrote LineageSettings $name=$value via call")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write LineageSettings $name", e)
        }
    }

    private fun writeSysfs(path: String, value: String): Boolean {
        return try {
            File(path).takeIf { it.exists() }?.let { file ->
                FileOutputStream(file).use { fos ->
                    fos.write(value.toByteArray())
                    fos.flush()
                }
                true
            } ?: run {
                Log.e(TAG, "Sysfs node $path does not exist")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to $path: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "ChargerUtils"
        const val CHARGER_MAIN_ENABLE = "device_charging_main_enable"
        const val CHARGER_LIMIT_ENABLE = "device_charging_limit_enable"
        const val CHARGER_HS_ENABLE = "device_charging_enable"
        const val CHARGER_MODE = "device_charging_mode"
        const val CHARGER_START_TIME = "device_charging_start_time"
        const val CHARGER_TARGET_TIME = "device_charging_target_time"
        const val CHARGER_MIGRATED = "device_charging_migrated_lineage"

        const val MODE_AUTO = 1
        const val MODE_MANUAL = 2
        const val MODE_LIMIT = 3

        // 22:00 / 06:00 (seconds of day) — giong mac dinh Lineage
        const val DEFAULT_START_TIME = 79200
        const val DEFAULT_TARGET_TIME = 21600
        private const val SCHEDULE_HOLD_LEVEL = 80

        private val CHG_INTERRUPTION_PATHS = arrayOf(
            "/sys/class/power_supply/battery_ext/smart_charging_interruption",
            "/sys/class/battchg_ext/smart_charging_interruption",
            "/sys/class/battchg_ext/smart_charging_interruption",
            "/sys/class/battchg_ext/smart_charging_interruption",
            "/sys/class/power_supply/battery_ext/smart_charging_interruption"
        )
    }
}
