/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Tich luy thong ke pin (cac StatsRange) — singleton dung chung Service + UI.
 * Hao pin uu tien charge_counter (uAh); fallback precise/% nguyen.
 */
class BatteryStatsTracker private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    private val ranges = Array(StatsRange.entries.size) { RangeAccum() }

    private var lastElapsed = 0L
    private var lastUptime = 0L
    private var lastLevel = -1
    private var lastPreciseCapacity = -1f
    private var lastChargeCounter = -1L
    private var lastScreenOn = true
    private var lastPlugged = false
    private var wasFull = false
    private var loaded = false
    private var dirty = false
    private var lastFlushElapsed = 0L

    private class RangeAccum {
        var screenOnMs = 0L
        var screenOffMs = 0L
        var screenOnDrain = 0f
        var screenOffDrain = 0f
        var deepSleepMs = 0L
        var heldAwakeMs = 0L
        var chargeMs = 0L

        fun clear() {
            screenOnMs = 0L
            screenOffMs = 0L
            screenOnDrain = 0f
            screenOffDrain = 0f
            deepSleepMs = 0L
            heldAwakeMs = 0L
            chargeMs = 0L
        }
    }

    @Synchronized
    fun resetAll() {
        loaded = true
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val interactive = powerManager?.isInteractive == true
        val snap = BatteryReader.read(appContext)

        ranges.forEach { it.clear() }
        lastElapsed = nowElapsed
        lastUptime = nowUptime
        lastLevel = snap?.levelPercent ?: -1
        lastPreciseCapacity = snap?.preciseCapacityPercent ?: -1f
        lastChargeCounter = snap?.chargeCounterUa ?: -1L
        lastScreenOn = interactive
        // Chi tin EXTRA_PLUGGED / charging cable — khong dung status CHARGING treo
        lastPlugged = snap?.pluggedType?.let { it != 0 } ?: (snap?.charging == true)
        wasFull = snap?.levelPercent == 100
        dirty = true
        flushLocked(force = true)
        Log.i(TAG, "all ranges reset plugged=$lastPlugged screen=$lastScreenOn")
    }

    @Synchronized
    fun resetRange(range: StatsRange) {
        ensureLoadedLocked()
        ranges[range.ordinal].clear()
        dirty = true
        flushLocked(force = true)
        Log.i(TAG, "range reset: $range")
    }

    @Synchronized
    fun ensureInitialized() {
        ensureLoadedLocked()
    }

    /**
     * @param plugged true khi co day sac (EXTRA_PLUGGED != 0), khong dung status.
     */
    @Synchronized
    fun onSample(
        levelPercent: Int,
        plugged: Boolean,
        snap: BatterySnapshot? = null
    ) {
        ensureLoadedLocked()

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val screenOn = powerManager?.isInteractive == true

        val dtElapsed = (nowElapsed - lastElapsed).coerceAtLeast(0L)
        // uptime dung khi CPU deep-sleep; elapsed van chay (giong DevCheck e()).
        // KHONG zero dtUptime khi gap dai — neu khong moi khoang man tat >5p bi tinh 100% deep sleep.
        val dtUptime = (nowUptime - lastUptime).coerceAtLeast(0L).coerceAtMost(dtElapsed)

        // Chi dung cho drain (charge_counter/%) — khong dung de suy deep sleep
        val largeGap = dtElapsed > MAX_TRUSTED_SAMPLE_MS

        // Bo qua mau dau (lastElapsed chua set) — tranh cong nham
        if (lastElapsed > 0L && dtElapsed > 0L && dtElapsed < MAX_ABSURD_GAP_MS) {
            StatsRange.entries.forEach { range ->
                accumulateLocked(
                    ranges[range.ordinal],
                    dtElapsed,
                    dtUptime,
                    lastScreenOn,
                    lastPlugged
                )
            }
        }

        val precise = snap?.preciseCapacityPercent ?: -1f
        val counter = snap?.chargeCounterUa ?: -1L
        val chargeFull = snap?.chargeFullUa ?: -1L

        // Hao pin theo trang thai TRUOC mau (lastPlugged) — tranh mat doan khi chuyen sac/xa
        if (!lastPlugged) {
            val drainPct = computeDrainPercentLocked(
                levelPercent, precise, counter, chargeFull, largeGap, dtElapsed
            )
            if (drainPct > 0f) {
                StatsRange.entries.forEach { range ->
                    val a = ranges[range.ordinal]
                    if (lastScreenOn) a.screenOnDrain += drainPct
                    else a.screenOffDrain += drainPct
                }
            }
        }

        val isFull = levelPercent == 100
        if (isFull && !wasFull) {
            ranges[StatsRange.SINCE_FULL.ordinal].clear()
            Log.i(TAG, "hit full — reset SINCE_FULL")
        }

        // Sau accumulate (sample van dung lastPlugged=true) — xoa sach tu luc rut sac
        if (lastPlugged && !plugged) {
            ranges[StatsRange.SINCE_UNPLUG.ordinal].clear()
            Log.i(TAG, "unplug — reset SINCE_UNPLUG")
        }

        // Bat pluggedChanged TRUOC khi gan lastPlugged — neu so sau gan thi luon false
        val pluggedChanged = lastPlugged != plugged
        if (pluggedChanged) {
            Log.d(TAG, "plugged $lastPlugged -> $plugged (chargeMs will stop/start)")
        }

        lastElapsed = nowElapsed
        lastUptime = nowUptime
        if (levelPercent in 0..100) lastLevel = levelPercent
        if (precise >= 0f) lastPreciseCapacity = precise
        if (counter > 0L) lastChargeCounter = counter
        lastScreenOn = screenOn
        lastPlugged = plugged
        wasFull = isFull
        dirty = true

        val shouldFlush = !screenOn ||
            largeGap ||
            pluggedChanged ||
            (nowElapsed - lastFlushElapsed) >= FLUSH_INTERVAL_MS
        if (shouldFlush) {
            flushLocked(force = false)
        }
    }

    @Synchronized
    fun snapshot(range: StatsRange, refreshSample: Boolean = false): BatteryStatsSnapshot {
        ensureLoadedLocked()
        if (refreshSample) {
            val snap = BatteryReader.read(appContext)
            if (snap != null) {
                onSample(snap.levelPercent, snap.pluggedType != 0, snap)
            }
        } else if (lastElapsed > 0L) {
            // Doc plugged tu sticky intent — tranh lastPlugged treo
            val snap = BatteryReader.read(appContext)
            val plugged = if (snap != null) snap.pluggedType != 0 else lastPlugged
            val level = if (snap != null && snap.levelPercent in 0..100) {
                snap.levelPercent
            } else {
                lastLevel
            }
            onSample(level, plugged, snap)
        }
        val a = ranges[range.ordinal]
        return BatteryStatsSnapshot(
            totalOnBatteryMs = a.screenOnMs + a.screenOffMs,
            screenOnMs = a.screenOnMs,
            screenOffMs = a.screenOffMs,
            screenOnDrainPercent = a.screenOnDrain,
            screenOffDrainPercent = a.screenOffDrain,
            deepSleepMs = a.deepSleepMs,
            heldAwakeMs = a.heldAwakeMs,
            chargeMs = a.chargeMs
        )
    }

    @Synchronized
    fun flush() {
        ensureLoadedLocked()
        flushLocked(force = true)
    }

    private fun computeDrainPercentLocked(
        levelPercent: Int,
        precise: Float,
        counter: Long,
        chargeFull: Long,
        largeGap: Boolean,
        dtElapsed: Long
    ): Float = BatteryStatsLogic.computeDrainPercent(
        lastLevel = lastLevel,
        levelPercent = levelPercent,
        lastPrecise = lastPreciseCapacity,
        precise = precise,
        lastCounter = lastChargeCounter,
        counter = counter,
        chargeFull = chargeFull,
        largeGap = largeGap,
        dtElapsed = dtElapsed
    )

    private fun accumulateLocked(
        a: RangeAccum,
        dtElapsed: Long,
        dtUptime: Long,
        screenOn: Boolean,
        plugged: Boolean
    ) {
        if (plugged) {
            a.chargeMs += dtElapsed
            return
        }
        if (screenOn) {
            a.screenOnMs += dtElapsed
        } else {
            // Man tat != deep sleep. Deep sleep = elapsed - uptime (CPU suspend).
            // held awake = phan man tat ma CPU van chay (wakelock / doze maintenance).
            a.screenOffMs += dtElapsed
            val sleep = (dtElapsed - dtUptime).coerceAtLeast(0L)
            a.deepSleepMs += sleep
            a.heldAwakeMs += dtUptime
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        if (!prefs.contains(KEY_LAST_ELAPSED)) {
            loaded = true
            resetAll()
            return
        }
        lastElapsed = prefs.getLong(KEY_LAST_ELAPSED, SystemClock.elapsedRealtime())
        lastUptime = prefs.getLong(KEY_LAST_UPTIME, SystemClock.uptimeMillis())
        lastLevel = prefs.getInt(KEY_LAST_LEVEL, -1)
        lastPreciseCapacity = prefs.getFloat(KEY_LAST_PRECISE, -1f)
        lastChargeCounter = prefs.getLong(KEY_LAST_COUNTER, -1L)
        lastScreenOn = prefs.getBoolean(KEY_LAST_SCREEN_ON, true)
        lastPlugged = prefs.getBoolean(KEY_LAST_PLUGGED, false)
        wasFull = prefs.getBoolean(KEY_WAS_FULL, false)
        StatsRange.entries.forEach { range ->
            val a = ranges[range.ordinal]
            val p = range.prefsPrefix
            a.screenOnMs = prefs.getLong(p + KEY_SCREEN_ON_MS, 0L)
            a.screenOffMs = prefs.getLong(p + KEY_SCREEN_OFF_MS, 0L)
            a.screenOnDrain = prefs.getFloat(p + KEY_SCREEN_ON_DRAIN, 0f)
            a.screenOffDrain = prefs.getFloat(p + KEY_SCREEN_OFF_DRAIN, 0f)
            a.deepSleepMs = prefs.getLong(p + KEY_DEEP_SLEEP_MS, 0L)
            a.heldAwakeMs = prefs.getLong(p + KEY_HELD_AWAKE_MS, 0L)
            a.chargeMs = prefs.getLong(p + KEY_CHARGE_MS, 0L)
        }
        // Dong bo phan cung sau restart — khong cong khoang chet, phat hien su kien offline ro rang
        val snap = BatteryReader.read(appContext)
        if (snap != null) {
            val level = snap.levelPercent
            val plugged = snap.pluggedType != 0
            val savedPlugged = lastPlugged
            val savedLevel = lastLevel

            var offlineEvent = false
            if (savedPlugged && !plugged) {
                ranges[StatsRange.SINCE_UNPLUG.ordinal].clear()
                offlineEvent = true
                Log.i(TAG, "load: unplug offline — reset SINCE_UNPLUG")
            }
            if (level == 100 && savedLevel in 0..99) {
                ranges[StatsRange.SINCE_FULL.ordinal].clear()
                wasFull = true
                offlineEvent = true
                Log.i(TAG, "load: full offline — reset SINCE_FULL")
            } else if (level in 0..99 && savedLevel == 100) {
                wasFull = false
                offlineEvent = true
            }
            if (offlineEvent) dirty = true

            lastPlugged = plugged
            lastScreenOn = powerManager?.isInteractive == true
            if (level in 0..100) lastLevel = level
            val counter = snap.chargeCounterUa
            if (counter > 0L) lastChargeCounter = counter
            val precise = snap.preciseCapacityPercent
            if (precise >= 0f) lastPreciseCapacity = precise
            // Dat moc thoi gian = bay gio — khong tu tinh bu khoang service chet
            lastElapsed = SystemClock.elapsedRealtime()
            lastUptime = SystemClock.uptimeMillis()
        }
        lastFlushElapsed = SystemClock.elapsedRealtime()
        loaded = true
    }

    private fun flushLocked(force: Boolean) {
        if (!dirty && !force) return
        val editor = prefs.edit()
            .putLong(KEY_LAST_ELAPSED, lastElapsed)
            .putLong(KEY_LAST_UPTIME, lastUptime)
            .putInt(KEY_LAST_LEVEL, lastLevel)
            .putFloat(KEY_LAST_PRECISE, lastPreciseCapacity)
            .putLong(KEY_LAST_COUNTER, lastChargeCounter)
            .putBoolean(KEY_LAST_SCREEN_ON, lastScreenOn)
            .putBoolean(KEY_LAST_PLUGGED, lastPlugged)
            .putBoolean(KEY_WAS_FULL, wasFull)
        StatsRange.entries.forEach { range ->
            val a = ranges[range.ordinal]
            val p = range.prefsPrefix
            editor
                .putLong(p + KEY_SCREEN_ON_MS, a.screenOnMs)
                .putLong(p + KEY_SCREEN_OFF_MS, a.screenOffMs)
                .putFloat(p + KEY_SCREEN_ON_DRAIN, a.screenOnDrain)
                .putFloat(p + KEY_SCREEN_OFF_DRAIN, a.screenOffDrain)
                .putLong(p + KEY_DEEP_SLEEP_MS, a.deepSleepMs)
                .putLong(p + KEY_HELD_AWAKE_MS, a.heldAwakeMs)
                .putLong(p + KEY_CHARGE_MS, a.chargeMs)
        }
        editor.apply()
        dirty = false
        lastFlushElapsed = SystemClock.elapsedRealtime()
    }

    companion object {
        private const val TAG = "BatteryStatsTracker"
        // v4: deep sleep dung elapsed-uptime ca khi gap dai (truoc do largeGap = 100% sleep)
        private const val PREFS_NAME = "xperia_battery_monitor_stats_v4"
        private const val MAX_TRUSTED_SAMPLE_MS = 5 * 60 * 1000L
        private const val MAX_ABSURD_GAP_MS = 7L * 24 * 60 * 60 * 1000
        private const val FLUSH_INTERVAL_MS = 5 * 60 * 1000L

        private const val KEY_LAST_ELAPSED = "last_elapsed"
        private const val KEY_LAST_UPTIME = "last_uptime"
        private const val KEY_LAST_LEVEL = "last_level"
        private const val KEY_LAST_PRECISE = "last_precise"
        private const val KEY_LAST_COUNTER = "last_counter"
        private const val KEY_LAST_SCREEN_ON = "last_screen_on"
        private const val KEY_LAST_PLUGGED = "last_plugged"
        private const val KEY_WAS_FULL = "was_full"

        private const val KEY_SCREEN_ON_MS = "screen_on_ms"
        private const val KEY_SCREEN_OFF_MS = "screen_off_ms"
        private const val KEY_SCREEN_ON_DRAIN = "screen_on_drain"
        private const val KEY_SCREEN_OFF_DRAIN = "screen_off_drain"
        private const val KEY_DEEP_SLEEP_MS = "deep_sleep_ms"
        private const val KEY_HELD_AWAKE_MS = "held_awake_ms"
        private const val KEY_CHARGE_MS = "charge_ms"

        @Volatile
        private var instance: BatteryStatsTracker? = null

        fun get(context: Context): BatteryStatsTracker {
            return instance ?: synchronized(this) {
                instance ?: BatteryStatsTracker(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
