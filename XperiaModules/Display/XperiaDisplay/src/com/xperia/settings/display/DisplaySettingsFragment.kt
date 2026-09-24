/*
 * Copyright (C) 2023-2024 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.os.Bundle

import androidx.preference.Preference

import com.android.settingslib.widget.LayoutPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

import com.xperia.settings.display.R

const val CREATOR_MODE_KEY = "switchCreatorMode"
const val XREALITY_MODE_KEY = "switchXRealityMode"

class DisplaySettingsFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {
    private lateinit var controller: DisplaySettingsController
    private var previewHelper: DisplayPreviewHelper? = null

    private val KEY_CREATOR_MODE_PREVIEW = "creator_mode_preview"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.display_settings, rootKey)
        controller = DisplaySettingsController(requireContext())
        controller.bind(this)

        val preview = findPreference<LayoutPreference>(KEY_CREATOR_MODE_PREVIEW)
        previewHelper = DisplayPreviewHelper.setup(preview!!, layoutInflater, requireContext())
        previewHelper?.restorePageIndex(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.display_settings_title)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        return controller.onPreferenceChange(this, preference, newValue)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        previewHelper?.savePageIndex(outState)
    }
}
