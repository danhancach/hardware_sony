/*
 * Copyright (C) 2025 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.app.AlarmManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.xperia.settings.charger.monitor.BatteryMonitorNotification
import com.xperia.settings.charger.monitor.BatteryMonitorPrefs
import com.xperia.settings.charger.monitor.BatteryReader
import com.xperia.settings.charger.monitor.BatteryStatsTracker
import com.xperia.settings.charger.monitor.StatsRange

/**
 * Service he thong: charging control + mau thong ke pin.
 * Thong bao: chi cap nhat theo su kien (khong poll 2s) de tiet kiem pin.
 */
class BatteryMonitorService : Service() {

    private lateinit var chargerUtils: ChargerUtils
    private lateinit var statsTracker: BatteryStatsTracker
    private lateinit var powerManager: PowerManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastBatteryLevel = -1
    private var lastPluggedState = -1
    private var lastNotifTitle: String? = null
    private var lastNotifText: String? = null
    private var lastNotifUiMode: Int = Int.MIN_VALUE

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val monitorUri = Settings.Global.getUriFor(BatteryMonitorPrefs.ENABLE_KEY)
            val notifUri = Settings.Global.getUriFor(BatteryMonitorNotification.SETTINGS_KEY)
            if (uri == monitorUri || uri == notifUri) {
                if (uri == monitorUri && BatteryMonitorPrefs.isEnabled(this@BatteryMonitorService)) {
                    statsTracker.ensureInitialized()
                }
                refreshNotification(force = true)
                return
            }
            chargerUtils.refreshFromBatteryIntent()
        }
    }

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    if (level != -1) {
                        if (level != lastBatteryLevel) {
                            Log.d(TAG, "Battery level updated: $level%")
                        }
                        lastBatteryLevel = level
                        chargerUtils.updateChargingState(level)
                    }

                    if (plugged >= 0) {
                        lastPluggedState = plugged
                    }

                    // Giam sat tat: van cap nhat sac/HSPC o tren; khong mau stats / notif
                    if (!BatteryMonitorPrefs.isEnabled(this@BatteryMonitorService)) {
                        return
                    }

                    val snap = BatteryReader.fromIntent(context, intent)
                    // Chi tin EXTRA_PLUGGED (>0 = dang cam); 0 = rut sac. Tranh default -1.
                    val isPlugged = plugged > 0
                    statsTracker.onSample(
                        if (level in 0..100) level else snap.levelPercent,
                        isPlugged,
                        snap
                    )
                    // Cap nhat notif khi level/temp doi; man tat thi bo qua
                    refreshNotification(force = false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (!BatteryMonitorPrefs.isEnabled(this@BatteryMonitorService)) return
                    val snap = BatteryReader.read(this@BatteryMonitorService)
                    val plugged = snap?.pluggedType?.let { it != 0 }
                        ?: (lastPluggedState > 0)
                    val level = snap?.levelPercent?.takeIf { it in 0..100 }
                        ?: lastBatteryLevel
                    statsTracker.onSample(level, plugged, snap)
                    refreshNotification(force = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (!BatteryMonitorPrefs.isEnabled(this@BatteryMonitorService)) return
                    val snap = BatteryReader.read(this@BatteryMonitorService)
                    val plugged = snap?.pluggedType?.let { it != 0 }
                        ?: (lastPluggedState > 0)
                    val level = snap?.levelPercent?.takeIf { it in 0..100 }
                        ?: lastBatteryLevel
                    statsTracker.onSample(level, plugged, snap)
                    statsTracker.flush()
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    // Doi theme / night: ep cap nhat mau chu thong bao
                    refreshNotification(force = true)
                }
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                    Log.d(TAG, "Next alarm changed, refresh schedule control")
                    chargerUtils.refreshFromBatteryIntent()
                }
            }
        }
    }

    private val periodicRefresh = object : Runnable {
        override fun run() {
            if (chargerUtils.mainSwitch &&
                (chargerUtils.chargingMode == ChargerUtils.MODE_AUTO ||
                    chargerUtils.chargingMode == ChargerUtils.MODE_MANUAL)
            ) {
                chargerUtils.refreshFromBatteryIntent()
            }
            if (BatteryMonitorPrefs.isEnabled(this@BatteryMonitorService) &&
                powerManager.isInteractive
            ) {
                val plugged = lastPluggedState != 0 && lastPluggedState != -1
                // Chi mau nhe khi man bat; man tat de SCREEN_OFF + doze xu ly
                statsTracker.onSample(
                    lastBatteryLevel, plugged,
                    BatteryReader.read(this@BatteryMonitorService)
                )
                refreshNotification(force = false)
            }
            handler.postDelayed(this, PERIODIC_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        chargerUtils = ChargerUtils(this)
        statsTracker = BatteryStatsTracker.get(this)
        powerManager = getSystemService(PowerManager::class.java)
        if (BatteryMonitorPrefs.isEnabled(this)) {
            statsTracker.ensureInitialized()
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)

        val cr = contentResolver
        listOf(
            ChargerUtils.CHARGER_MAIN_ENABLE,
            ChargerUtils.CHARGER_LIMIT_ENABLE,
            ChargerUtils.CHARGER_HS_ENABLE,
            ChargerUtils.CHARGER_MODE,
            ChargerUtils.CHARGER_START_TIME,
            ChargerUtils.CHARGER_TARGET_TIME,
            BatteryMonitorPrefs.ENABLE_KEY,
            BatteryMonitorNotification.SETTINGS_KEY
        ).forEach { key ->
            cr.registerContentObserver(
                Settings.Global.getUriFor(key), true, settingsObserver
            )
        }

        handler.postDelayed(periodicRefresh, PERIODIC_MS)
        refreshNotification(force = true)
        Log.d(TAG, "BatteryMonitorService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        chargerUtils.refreshFromBatteryIntent()
        if (BatteryMonitorPrefs.isEnabled(this)) {
            statsTracker.ensureInitialized()
        }
        refreshNotification(force = true)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(periodicRefresh)
        statsTracker.flush()
        contentResolver.unregisterContentObserver(settingsObserver)
        unregisterReceiver(batteryReceiver)
        Log.d(TAG, "onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshNotification(force: Boolean) {
        try {
            if (!BatteryMonitorPrefs.isEnabled(this) ||
                !BatteryMonitorNotification.isEnabled(this)
            ) {
                BatteryMonitorNotification.cancel(this)
                lastNotifTitle = null
                lastNotifText = null
                lastNotifUiMode = Int.MIN_VALUE
                return
            }
            // Man tat: khong cap nhat (tru force khi bat toggle / doi theme)
            if (!force && !powerManager.isInteractive) return

            val snap = BatteryReader.read(this) ?: return
            val stats = statsTracker.snapshot(StatsRange.SINCE_START, refreshSample = false)
            val posted = BatteryMonitorNotification.updateIfChanged(
                this, snap, stats, lastNotifTitle, lastNotifText,
                force = force,
                prevUiMode = lastNotifUiMode
            )
            if (posted != null) {
                lastNotifTitle = posted.first
                lastNotifText = posted.second
                lastNotifUiMode = posted.third
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshNotification failed", e)
        }
    }

    companion object {
        private const val TAG = "BatteryMonitorService"
        /** Mau dinh ky khi man bat — du cho hao %/h, khong wake khi doze */
        private const val PERIODIC_MS = 5 * 60_000L

        fun startService(context: Context) {
            val serviceIntent = Intent(context, BatteryMonitorService::class.java)
            context.startServiceAsUser(serviceIntent, UserHandle.SYSTEM)
        }
    }
}
