/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.android.settingslib.widget.MainSwitchBar
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.xperia.settings.charger.monitor.BatteryReader
import com.xperia.settings.charger.monitor.BatterySnapshot
import com.xperia.settings.charger.monitor.BatteryMonitorNotification
import com.xperia.settings.charger.monitor.BatteryMonitorPrefs
import com.xperia.settings.charger.monitor.BatteryStatsSnapshot
import com.xperia.settings.charger.monitor.BatteryStatsTracker
import com.xperia.settings.charger.monitor.StatsRange
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class BatteryMonitorFragment : Fragment() {

    private lateinit var statsTracker: BatteryStatsTracker
    private val handler = Handler(Looper.getMainLooper())
    private var selectedRange: StatsRange = StatsRange.SINCE_START
    private var spinnerReady = false
    private var monitorEnabled = true

    private var mainSwitchBar: MainSwitchBar? = null
    private var contentView: View? = null

    private var levelView: TextView? = null
    private var tempView: TextView? = null
    private var currentView: TextView? = null
    private var statusView: TextView? = null
    private var powerView: TextView? = null

    private var totalTimeView: TextView? = null
    private var screenOnTimeView: TextView? = null
    private var screenOnUsageView: TextView? = null
    private var screenOnDrainView: TextView? = null
    private var screenOffTimeView: TextView? = null
    private var screenOffUsageView: TextView? = null
    private var deepSleepView: TextView? = null
    private var heldAwakeView: TextView? = null
    private var screenOffDrainView: TextView? = null
    private var chargeTimeView: TextView? = null
    private var lastSnap: BatterySnapshot? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!monitorEnabled) return
            // Poll nhe: chi can current/power realtime; level/temp tu BATTERY_CHANGED
            refreshUiCurrentOnly()
            handler.postDelayed(this, UI_REFRESH_MS)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!monitorEnabled) return
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                refreshUi(intent)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.battery_monitor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statsTracker = BatteryStatsTracker.get(requireContext())

        contentView = view.findViewById(R.id.battery_monitor_content)
        setupMainSwitch(view.findViewById(R.id.battery_monitor_main_switch))

        levelView = view.findViewById(R.id.batmon_level)
        // Font % vua dam (khong qua dam/nhat)
        // % pin: to hon mot chut, dam vua (500)
        levelView?.typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
        tempView = view.findViewById(R.id.batmon_temp)
        currentView = view.findViewById(R.id.batmon_current)
        statusView = view.findViewById(R.id.batmon_status)
        powerView = view.findViewById(R.id.batmon_power)

        totalTimeView = view.findViewById(R.id.battery_time)
        screenOnTimeView = view.findViewById(R.id.screen_on)
        screenOnUsageView = view.findViewById(R.id.screen_on_usage)
        screenOnDrainView = view.findViewById(R.id.screen_drain)
        screenOffTimeView = view.findViewById(R.id.screen_off)
        screenOffUsageView = view.findViewById(R.id.screen_off_usage)
        deepSleepView = view.findViewById(R.id.bm_deepsleep)
        heldAwakeView = view.findViewById(R.id.held_awake)
        screenOffDrainView = view.findViewById(R.id.idle_drain)
        chargeTimeView = view.findViewById(R.id.charge_time)

        setupSpinner(view.findViewById(R.id.battery_spinner))
        setupMenu()
    }

    private fun setupMainSwitch(bar: MainSwitchBar) {
        mainSwitchBar = bar
        bar.setTitle(getString(R.string.battery_monitor_enable))
        bar.show()
        val enabled = BatteryMonitorPrefs.isEnabled(requireContext())
        // Dat trang thai truoc listener de tranh ghi Global lai khi bind
        monitorEnabled = enabled
        contentView?.visibility = if (enabled) View.VISIBLE else View.GONE
        bar.isChecked = enabled
        bar.addOnSwitchChangeListener { _, isChecked ->
            if (isChecked == monitorEnabled) return@addOnSwitchChangeListener
            BatteryMonitorPrefs.setEnabled(requireContext(), isChecked)
            applyEnabledState(isChecked, refresh = true)
            BatteryMonitorService.startService(requireContext())
        }
    }

    private fun applyEnabledState(enabled: Boolean, refresh: Boolean) {
        monitorEnabled = enabled
        contentView?.visibility = if (enabled) View.VISIBLE else View.GONE
        activity?.invalidateOptionsMenu()
        handler.removeCallbacks(refreshRunnable)
        if (enabled && refresh && isResumed) {
            refreshUi()
            handler.post(refreshRunnable)
        }
    }

    private fun setupSpinner(spinner: Spinner) {
        val labels = resources.getStringArray(R.array.battery_monitor_range_entries)
        spinner.adapter = ArrayAdapter(
            requireContext(),
            R.layout.battery_monitor_spinner_item,
            labels
        ).also {
            it.setDropDownViewResource(R.layout.battery_monitor_spinner_dropdown_item)
        }
        spinner.setSelection(0, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val range = when (position) {
                    1 -> StatsRange.SINCE_FULL
                    2 -> StatsRange.SINCE_NOW
                    else -> StatsRange.SINCE_START
                }
                if (!spinnerReady) {
                    spinnerReady = true
                    selectedRange = range
                    return
                }
                if (range == StatsRange.SINCE_NOW && selectedRange != StatsRange.SINCE_NOW) {
                    // Chon "Ke tu bay gio" — bat dau dem lai tu luc nay
                    statsTracker.resetRange(StatsRange.SINCE_NOW)
                }
                selectedRange = range
                refreshUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.battery_monitor, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val monitorOn = BatteryMonitorPrefs.isEnabled(requireContext())
                menu.findItem(R.id.action_show_notification)?.isVisible = monitorOn
                menu.findItem(R.id.action_reset)?.isVisible = monitorOn
                if (!monitorOn) return
                val enabled = BatteryMonitorNotification.isEnabled(requireContext())
                menu.findItem(R.id.action_show_notification)?.setTitle(
                    if (enabled) {
                        R.string.battery_monitor_disable_notification
                    } else {
                        R.string.battery_monitor_enable_notification
                    }
                )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (!BatteryMonitorPrefs.isEnabled(requireContext())) return false
                return when (menuItem.itemId) {
                    R.id.action_show_notification -> {
                        val enable = !BatteryMonitorNotification.isEnabled(requireContext())
                        BatteryMonitorNotification.setEnabled(requireContext(), enable)
                        BatteryMonitorService.startService(requireContext())
                        true
                    }
                    R.id.action_reset -> {
                        statsTracker.resetAll()
                        refreshUi()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.battery_monitor_title)
        // Tieu de nho mac dinh (dong bo Settings he thong)
        collapseCollapsingToolbar()
        BatteryMonitorService.startService(requireContext())
        monitorEnabled = BatteryMonitorPrefs.isEnabled(requireContext())
        mainSwitchBar?.isChecked = monitorEnabled
        applyEnabledState(monitorEnabled, refresh = false)
        requireContext().registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
        if (monitorEnabled) {
            refreshUi()
            handler.post(refreshRunnable)
        }
    }

    /** Tieu de nho + mau accent. */
    private fun collapseCollapsingToolbar() {
        val act = activity ?: return
        act.findViewById<AppBarLayout>(
            com.android.settingslib.collapsingtoolbar.R.id.app_bar
        )?.setExpanded(false, false)

        val collapsing = act.findViewById<CollapsingToolbarLayout>(
            com.android.settingslib.collapsingtoolbar.R.id.collapsing_toolbar
        ) ?: return
        val accent = act.getColor(R.color.battery_monitor_accent)
        val csl = ColorStateList.valueOf(accent)
        collapsing.setCollapsedTitleTextColor(csl)
        collapsing.setExpandedTitleTextColor(csl)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
        try {
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    private fun refreshUi(batteryIntent: Intent? = null) {
        val ctx = context ?: return
        val snap = if (batteryIntent != null) {
            BatteryReader.fromIntent(ctx, batteryIntent)
        } else {
            BatteryReader.read(ctx) ?: return
        }
        lastSnap = snap
        bindRealtime(snap)
        // Stats: cap nhat thoi gian tu RAM, khong bat buoc doc sysfs lan nua
        bindStats(statsTracker.snapshot(selectedRange, refreshSample = false))
    }

    /** Poll sysfs current/voltage thua; giu level/temp tu mau gan nhat. */
    private fun refreshUiCurrentOnly() {
        val base = lastSnap ?: run {
            refreshUi()
            return
        }
        val (currentUa, voltageUv) = BatteryReader.readCurrentVoltage()
        val updated = base.copy(
            currentUa = currentUa,
            voltageUv = if (voltageUv > 0L) voltageUv else base.voltageUv,
            powerNowUw = Long.MIN_VALUE
        )
        lastSnap = updated
        bindRealtime(updated)
        bindStats(statsTracker.snapshot(selectedRange, refreshSample = false))
    }

    private fun bindRealtime(snap: BatterySnapshot) {
        levelView?.text = getString(R.string.battery_monitor_level_format, snap.levelPercent)
        tempView?.text = getString(
            R.string.battery_monitor_temp_format,
            snap.temperatureC.roundToInt()
        )
        currentView?.text = getString(
            R.string.battery_monitor_current_format,
            snap.signedCurrentMa.roundToInt()
        )
        statusView?.text = formatStatus(snap)
        powerView?.text = getString(
            R.string.battery_monitor_power_format,
            String.format(Locale.getDefault(), "%.1f", snap.signedPowerW)
        )
    }

    private fun bindStats(stats: BatteryStatsSnapshot) {
        totalTimeView?.text = getString(
            R.string.battery_monitor_on_battery_time,
            formatDuration(stats.totalOnBatteryMs)
        )
        screenOnTimeView?.text = getString(
            R.string.battery_monitor_time_format,
            formatDuration(stats.screenOnMs)
        )
        screenOnUsageView?.text = getString(
            R.string.battery_monitor_usage_format,
            stats.screenOnDrainPercent.roundToInt()
        )
        screenOnDrainView?.text = getString(
            R.string.battery_monitor_active_drain,
            String.format(Locale.getDefault(), "%.2f", stats.screenOnDrainPerHour)
        )

        screenOffTimeView?.text = getString(
            R.string.battery_monitor_time_format,
            formatDuration(stats.screenOffMs)
        )
        screenOffUsageView?.text = getString(
            R.string.battery_monitor_usage_format,
            stats.screenOffDrainPercent.roundToInt()
        )
        deepSleepView?.text = getString(
            R.string.battery_monitor_deep_sleep,
            formatDuration(stats.deepSleepMs)
        )
        heldAwakeView?.text = getString(
            R.string.battery_monitor_held_awake,
            formatDuration(stats.heldAwakeMs),
            String.format(Locale.getDefault(), "%.1f", stats.heldAwakePercentOfScreenOff)
        )
        screenOffDrainView?.text = getString(
            R.string.battery_monitor_idle_drain,
            String.format(Locale.getDefault(), "%.2f", stats.screenOffDrainPerHour)
        )
        chargeTimeView?.text = getString(
            R.string.battery_monitor_charge_time,
            formatDuration(stats.chargeMs)
        )
    }

    private fun formatStatus(snap: BatterySnapshot): String {
        if (!snap.charging && snap.status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return getString(R.string.battery_monitor_status_discharging)
        }
        if (!snap.charging && snap.status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            return getString(R.string.battery_monitor_status_not_charging)
        }
        if (snap.status == BatteryManager.BATTERY_STATUS_FULL) {
            return getString(R.string.battery_monitor_status_full)
        }
        return when (snap.pluggedType) {
            BatteryManager.BATTERY_PLUGGED_AC ->
                getString(R.string.battery_monitor_status_charging_ac)
            BatteryManager.BATTERY_PLUGGED_USB ->
                getString(R.string.battery_monitor_status_charging_usb)
            BatteryManager.BATTERY_PLUGGED_WIRELESS ->
                getString(R.string.battery_monitor_status_charging_wireless)
            BatteryManager.BATTERY_PLUGGED_DOCK ->
                getString(R.string.battery_monitor_status_charging_dock)
            else -> if (snap.charging) {
                getString(R.string.battery_monitor_status_charging)
            } else {
                getString(R.string.battery_monitor_status_discharging)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        var remain = ms.coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(remain)
        remain -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remain)
        remain -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remain)
        return when {
            hours > 0L -> getString(R.string.battery_monitor_duration_hm, hours, minutes)
            minutes > 0L -> getString(R.string.battery_monitor_duration_ms, minutes, seconds)
            else -> getString(R.string.battery_monitor_duration_s, seconds)
        }
    }

    companion object {
        // Current/power ~1 Hz khi UI mo; level/temp theo BATTERY_CHANGED
        private const val UI_REFRESH_MS = 1000L
    }
}
