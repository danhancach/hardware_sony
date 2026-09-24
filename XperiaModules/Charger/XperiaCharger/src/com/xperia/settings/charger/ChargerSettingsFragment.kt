/*
 * Copyright (C) 2025 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xperia.settings.charger.ChargerUtils.Companion.CHARGER_HS_ENABLE
import com.xperia.settings.charger.ChargerUtils.Companion.CHARGER_LIMIT_ENABLE
import com.xperia.settings.charger.ChargerUtils.Companion.CHARGER_MAIN_ENABLE
import com.xperia.settings.charger.ChargerUtils.Companion.CHARGER_MODE
import com.xperia.settings.charger.ChargerUtils.Companion.MODE_LIMIT
import com.xperia.settings.charger.ChargerUtils.Companion.MODE_MANUAL
import com.xperia.settings.charger.widgets.ChargingLimitPreference
import com.xperia.settings.charger.widgets.TimeOfDayPreference

class ChargerSettingsFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceChangeListener {

    private lateinit var chargerUtils: ChargerUtils

    private var mSwitch: MainSwitchPreference? = null
    private var mModePref: ListPreference? = null
    private var mChargingSwitch: SwitchPreferenceCompat? = null
    private var mChargingLimit: ChargingLimitPreference? = null
    private var mStartTime: TimeOfDayPreference? = null
    private var mTargetTime: TimeOfDayPreference? = null

    private lateinit var settingsObserver: ContentObserver

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.charger_settings, rootKey)
        chargerUtils = ChargerUtils(requireContext())

        mSwitch = findPreference<MainSwitchPreference>(KEY_MAIN)?.apply {
            isChecked = chargerUtils.mainSwitch
            onPreferenceChangeListener = this@ChargerSettingsFragment
        }

        mModePref = findPreference<ListPreference>(KEY_MODE)?.apply {
            value = chargerUtils.chargingMode.toString()
            onPreferenceChangeListener = this@ChargerSettingsFragment
        }

        mChargingSwitch = findPreference<SwitchPreferenceCompat>(KEY_HSPC)?.apply {
            isChecked = chargerUtils.isHSPCEnabled
            onPreferenceChangeListener = this@ChargerSettingsFragment
        }

        mChargingLimit = findPreference<ChargingLimitPreference>(KEY_LIMIT)?.apply {
            setMin(resources.getInteger(R.integer.charging_control_min))
            setMax(resources.getInteger(R.integer.charging_control_max))
            value = chargerUtils.chargingLimit
            onPreferenceChangeListener = this@ChargerSettingsFragment
        }

        mStartTime = findPreference(KEY_START)
        mTargetTime = findPreference(KEY_TARGET)
        mStartTime?.onPreferenceChangeListener = this
        mTargetTime?.onPreferenceChangeListener = this
        mStartTime?.setValue(chargerUtils.startTimeSeconds)
        mTargetTime?.setValue(chargerUtils.targetTimeSeconds)

        refreshModeVisibility()

        settingsObserver = object : ContentObserver(Handler(requireContext().mainLooper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                mSwitch?.isChecked = chargerUtils.mainSwitch
                mChargingSwitch?.isChecked = chargerUtils.isHSPCEnabled
                mChargingLimit?.value = chargerUtils.chargingLimit
                mModePref?.value = chargerUtils.chargingMode.toString()
                mStartTime?.setValue(chargerUtils.startTimeSeconds)
                mTargetTime?.setValue(chargerUtils.targetTimeSeconds)
                refreshModeVisibility()
            }
        }
    }

    private fun refreshModeVisibility() {
        val mode = chargerUtils.chargingMode
        val enabled = chargerUtils.mainSwitch
        mChargingLimit?.isVisible = enabled && mode == MODE_LIMIT
        mStartTime?.isVisible = enabled && mode == MODE_MANUAL
        mTargetTime?.isVisible = enabled && mode == MODE_MANUAL
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            KEY_MAIN -> {
                handleMainSwitchChange(newValue as? Boolean ?: return false)
                refreshModeVisibility()
                return true
            }
            KEY_MODE -> {
                val mode = (newValue as? String)?.toIntOrNull() ?: return false
                chargerUtils.chargingMode = mode
                refreshModeVisibility()
                chargerUtils.refreshFromBatteryIntent()
                return true
            }
            KEY_HSPC -> {
                chargerUtils.isHSPCEnabled = newValue as Boolean
                return true
            }
            KEY_LIMIT -> {
                chargerUtils.chargingLimit = newValue as Int
                chargerUtils.refreshFromBatteryIntent()
                return true
            }
            KEY_START -> {
                chargerUtils.startTimeSeconds = newValue as Int
                chargerUtils.refreshFromBatteryIntent()
                return true
            }
            KEY_TARGET -> {
                chargerUtils.targetTimeSeconds = newValue as Int
                chargerUtils.refreshFromBatteryIntent()
                return true
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.charger_settings_title)

        val resolver = requireContext().contentResolver
        listOf(
            CHARGER_MAIN_ENABLE,
            CHARGER_LIMIT_ENABLE,
            CHARGER_HS_ENABLE,
            CHARGER_MODE
        ).forEach { key ->
            resolver.registerContentObserver(
                Settings.Global.getUriFor(key), true, settingsObserver
            )
        }
        settingsObserver.onChange(false, null)
    }

    override fun onPause() {
        super.onPause()
        requireContext().contentResolver.unregisterContentObserver(settingsObserver)
    }

    private fun handleMainSwitchChange(isChecked: Boolean) {
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (!isChecked) {
            // Chi backup limit/mode. HSPC doc lap (GameSpace / tile / switch) — khong xoa.
            sharedPreferences.edit()
                .putInt(KEY_LIMIT_BACKUP, chargerUtils.chargingLimit)
                .putInt(KEY_MODE_BACKUP, chargerUtils.chargingMode)
                .apply()
        } else {
            val prefLimit = sharedPreferences.getInt(KEY_LIMIT_BACKUP, 100)
            val prefMode = sharedPreferences.getInt(KEY_MODE_BACKUP, MODE_LIMIT)
            chargerUtils.chargingLimit = prefLimit
            chargerUtils.chargingMode = prefMode
            mChargingLimit?.value = prefLimit
            mModePref?.value = prefMode.toString()
        }

        chargerUtils.mainSwitch = isChecked
    }

    companion object {
        private const val KEY_MAIN = "device_charging_main_enable"
        private const val KEY_MODE = "device_charging_mode"
        private const val KEY_HSPC = "device_charging_enable"
        private const val KEY_LIMIT = "device_charging_control"
        private const val KEY_START = "device_charging_start_time"
        private const val KEY_TARGET = "device_charging_target_time"
        private const val KEY_LIMIT_BACKUP = "device_charging_control_backup"
        private const val KEY_MODE_BACKUP = "device_charging_mode_backup"
    }
}
