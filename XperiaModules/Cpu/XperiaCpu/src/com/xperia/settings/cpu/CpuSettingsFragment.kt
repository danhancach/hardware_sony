/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.cpu

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class CpuSettingsFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceChangeListener {

    private lateinit var utils: CpuUtils
    private var idlePref: ListPreference? = null
    private var activePref: ListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.cpu_settings, rootKey)
        utils = CpuUtils(requireContext())
        CpuService.start(requireContext())

        idlePref = findPreference<ListPreference>(KEY_IDLE)?.apply {
            value = utils.idleMode
            onPreferenceChangeListener = this@CpuSettingsFragment
        }
        activePref = findPreference<ListPreference>(KEY_ACTIVE)?.apply {
            value = utils.activeLimitPercent.toString()
            onPreferenceChangeListener = this@CpuSettingsFragment
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            KEY_IDLE -> {
                val mode = newValue as? String ?: return false
                utils.idleMode = mode
                utils.applyCurrent()
                return true
            }
            KEY_ACTIVE -> {
                val pct = (newValue as? String)?.toIntOrNull() ?: return false
                utils.activeLimitPercent = pct
                utils.applyCurrent()
                return true
            }
        }
        return false
    }

    companion object {
        private const val KEY_IDLE = "xperia_cpu_idle_mode"
        private const val KEY_ACTIVE = "xperia_cpu_active_limit"
    }
}
