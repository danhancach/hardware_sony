/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.cpu

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Chi dieu chinh scaling_max + governor + hotplug CPU7.
 * Khong ghi scaling_min — de stock/PerfHAL giu san.
 *
 * Active (man bat): cap scaling_max theo %, walt, CPU7 online.
 * Idle (man tat): powersave | 35% | 50% (+ offline CPU7).
 */
class CpuUtils(private val context: Context) {

    /** off | powersave | 35 | 50 */
    var idleMode: String
        get() {
            val raw = Settings.Global.getString(context.contentResolver, KEY_IDLE_MODE)
            if (raw != null && raw in IDLE_MODES) return raw
            // Migrate toggle cu (0/1) -> 35
            if (Settings.Global.getInt(context.contentResolver, KEY_IDLE_LIMIT_LEGACY, 0) == 1) {
                Settings.Global.putString(context.contentResolver, KEY_IDLE_MODE, MODE_35)
                Settings.Global.putInt(context.contentResolver, KEY_IDLE_LIMIT_LEGACY, 0)
                return MODE_35
            }
            return MODE_OFF
        }
        set(value) {
            val v = if (value in IDLE_MODES) value else MODE_OFF
            Settings.Global.putString(context.contentResolver, KEY_IDLE_MODE, v)
        }

    var activeLimitPercent: Int
        get() {
            val v = Settings.Global.getInt(context.contentResolver, KEY_ACTIVE_LIMIT, 100)
            return if (v in ACTIVE_PERCENTS) v else 100
        }
        set(value) {
            Settings.Global.putInt(
                context.contentResolver,
                KEY_ACTIVE_LIMIT,
                if (value in ACTIVE_PERCENTS) value else 100
            )
        }

    fun isScreenOn(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    fun applyCurrent() {
        val mode = idleMode
        val pct = activeLimitPercent
        when {
            !isScreenOn() && mode != MODE_OFF -> applyIdle(mode)
            !isScreenOn() || pct >= 100 -> restoreDefaults()
            else -> applyActive(pct)
        }
    }

    fun applyActive(percent: Int = activeLimitPercent) {
        ensureCpu7Online()
        for (policy in POLICIES_ALL) {
            setPolicyMaxPercent(policy, percent)
            setGovernor(policy, GOV_WALT)
        }
        Log.i(TAG, "applyActive percent=$percent")
    }

    fun applyIdle(mode: String = idleMode) {
        when (mode) {
            MODE_POWERSAVE -> applyIdlePowersave()
            MODE_35 -> applyIdlePercent(35)
            MODE_50 -> applyIdlePercent(50)
            else -> Log.w(TAG, "applyIdle ignore mode=$mode")
        }
    }

    /** Chi doi governor → powersave, roi offline CPU7. Khong doi max. */
    private fun applyIdlePowersave() {
        ensureCpu7Online()
        for (policy in POLICIES_ALL) {
            setGovernor(policy, GOV_POWERSAVE)
        }
        offlineCpu7()
        Log.i(TAG, "applyIdle powersave + cpu7 off")
    }

    private fun applyIdlePercent(percent: Int) {
        ensureCpu7Online()
        for (policy in POLICIES_ALL) {
            setPolicyMaxPercent(policy, percent)
            setGovernor(policy, GOV_WALT)
        }
        offlineCpu7()
        Log.i(TAG, "applyIdle ${percent}% + cpu7 off")
    }

    fun restoreDefaults() {
        ensureCpu7Online()
        for (policy in POLICIES_ALL) {
            val hw = readLong(policyPath(policy, "cpuinfo_max_freq")) ?: continue
            val avail = readAvail(policy)
            val freq = pickNearest(hw, avail) ?: hw
            writeLongIfChanged(policyPath(policy, "scaling_max_freq"), freq)
            setGovernor(policy, GOV_WALT)
        }
        Log.i(TAG, "restoreDefaults")
    }

    private fun setPolicyMaxPercent(policy: Int, percent: Int) {
        val hw = readLong(policyPath(policy, "cpuinfo_max_freq")) ?: return
        val avail = readAvail(policy)
        val freq = pickNearest(hw * percent / 100, avail) ?: return
        writeLongIfChanged(policyPath(policy, "scaling_max_freq"), freq)
    }

    private fun setGovernor(policy: Int, gov: String) {
        val cur = readText(policyPath(policy, "scaling_governor"))?.trim()
        if (cur != gov) {
            writeText(policyPath(policy, "scaling_governor"), gov)
        }
    }

    private fun ensureCpu7Online() {
        writeText(CORE_CTL_MAX, "1")
        writeText(CORE_CTL_MIN, "0")
        writeText(CPU7_ONLINE, "1")
    }

    private fun offlineCpu7() {
        writeText(CORE_CTL_MAX, "0")
        writeText(CORE_CTL_MIN, "0")
        writeText(CPU7_ONLINE, "0")
    }

    private fun readAvail(policy: Int): List<Long> {
        val raw = readText(policyPath(policy, "scaling_available_frequencies")) ?: return emptyList()
        return raw.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
    }

    private fun pickNearest(target: Long, avail: List<Long>): Long? {
        if (avail.isEmpty()) return null
        return avail.minByOrNull { kotlin.math.abs(it - target) }
    }

    private fun policyPath(policy: Int, node: String): String =
        "$CPUFREQ/policy$policy/$node"

    private fun readLong(path: String): Long? =
        readText(path)?.trim()?.toLongOrNull()

    private fun readText(path: String): String? = try {
        File(path).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        Log.w(TAG, "read fail $path: ${e.message}")
        null
    }

    private fun writeLongIfChanged(path: String, value: Long) {
        if (readLong(path) != value) {
            writeText(path, value.toString())
        }
    }

    private fun writeText(path: String, value: String) {
        try {
            val f = File(path)
            if (!f.exists()) {
                Log.w(TAG, "missing $path")
                return
            }
            f.writeText(value)
        } catch (e: Exception) {
            Log.e(TAG, "write fail $path=$value: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "XperiaCpu"
        const val KEY_IDLE_MODE = "xperia_cpu_idle_mode"
        /** Toggle cu — chi migrate sang KEY_IDLE_MODE. */
        const val KEY_IDLE_LIMIT_LEGACY = "xperia_cpu_idle_limit"
        const val KEY_ACTIVE_LIMIT = "xperia_cpu_active_limit"

        const val MODE_OFF = "off"
        const val MODE_POWERSAVE = "powersave"
        const val MODE_35 = "35"
        const val MODE_50 = "50"
        private val IDLE_MODES = setOf(MODE_OFF, MODE_POWERSAVE, MODE_35, MODE_50)
        private val ACTIVE_PERCENTS = setOf(70, 80, 90, 100)

        private const val CPUFREQ = "/sys/devices/system/cpu/cpufreq"
        private const val CPU7_ONLINE = "/sys/devices/system/cpu/cpu7/online"
        private const val CORE_CTL_MAX = "/sys/devices/system/cpu/cpu7/core_ctl/max_cpus"
        private const val CORE_CTL_MIN = "/sys/devices/system/cpu/cpu7/core_ctl/min_cpus"

        private const val POLICY_LITTLE = 0
        private const val POLICY_GOLD = 3
        private const val POLICY_PRIME = 7
        private val POLICIES_ALL = intArrayOf(POLICY_LITTLE, POLICY_GOLD, POLICY_PRIME)

        private const val GOV_WALT = "walt"
        private const val GOV_POWERSAVE = "powersave"
    }
}
