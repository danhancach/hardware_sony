/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.os.Bundle

import androidx.preference.Preference

import com.android.settingslib.widget.SettingsBasePreferenceFragment

class DisplaySettingsControlsFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {
    private lateinit var controller: DisplaySettingsController

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.display_settings_controls, rootKey)
        controller = DisplaySettingsController(requireContext())
        controller.bind(this)
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.display_settings_title)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        return controller.onPreferenceChange(this, preference, newValue)
    }
}
