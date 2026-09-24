/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.provider.Settings
import android.util.Log
import com.xperia.settings.charger.BatteryMonitorActivity
import com.xperia.settings.charger.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Thong bao ongoing pin (template he thong — mau chu theo shade/theme):
 *   Pin | 95% | 35°C
 *   20,69%/h | 0,00%/h
 *
 * Khong dung custom RemoteViews (de lech mau / inflate loi).
 * Icon: ic_stat_* monochrome, khong ?attr.
 */
object BatteryMonitorNotification {

    const val SETTINGS_KEY = "xperia_battery_monitor_notification"
    private const val TAG = "BatteryMonitorNotif"
    private const val CHANNEL_ID = "battery_monitor"
    private const val NOTIFICATION_ID = 26001

    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, SETTINGS_KEY, 0) == 1

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled && !BatteryMonitorPrefs.isEnabled(context)) {
            return
        }
        Settings.Global.putInt(context.contentResolver, SETTINGS_KEY, if (enabled) 1 else 0)
        if (enabled) {
            update(context)
        } else {
            cancel(context)
        }
    }

    fun update(
        context: Context,
        snap: BatterySnapshot? = null,
        stats: BatteryStatsSnapshot? = null
    ) {
        updateIfChanged(context, snap, stats, null, null, force = true)
    }

    /**
     * @return Triple(title, text, uiMode) khi da notify; null neu bo qua / loi.
     */
    fun updateIfChanged(
        context: Context,
        snap: BatterySnapshot?,
        stats: BatteryStatsSnapshot?,
        prevTitle: String?,
        prevText: String?,
        force: Boolean = false,
        prevUiMode: Int = Int.MIN_VALUE
    ): Triple<String, String, Int>? {
        if (!BatteryMonitorPrefs.isEnabled(context) || !isEnabled(context)) {
            cancel(context)
            return null
        }

        // Dung applicationContext (khong createConfigurationContext) de NM/PendingIntent on dinh.
        val appCtx = context.applicationContext
        val uiMode = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        val snapshot = snap ?: BatteryReader.read(appCtx) ?: return null
        val drain = stats ?: BatteryStatsTracker.get(appCtx).snapshot(StatsRange.SINCE_START, false)
        ensureChannel(appCtx)

        val tempC = snapshot.temperatureC.roundToInt()
        val title = appCtx.getString(
            R.string.battery_monitor_notification_title,
            snapshot.levelPercent,
            tempC
        )
        val active = String.format(Locale.getDefault(), "%.2f", drain.screenOnDrainPerHour)
        val idle = String.format(Locale.getDefault(), "%.2f", drain.screenOffDrainPerHour)
        val text = appCtx.getString(
            R.string.battery_monitor_notification_text,
            active,
            idle
        )

        if (!force && title == prevTitle && text == prevText && uiMode == prevUiMode) {
            return null
        }

        val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return null
        val open = PendingIntent.getActivity(
            appCtx,
            0,
            Intent(appCtx, BatteryMonitorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            val notification = Notification.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_battery_monitor)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setRequestPromotedOngoing(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
            Triple(title, text, uiMode)
        } catch (e: Exception) {
            Log.e(TAG, "notify failed", e)
            null
        }
    }

    fun cancel(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.battery_monitor_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.battery_monitor_notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }
}
