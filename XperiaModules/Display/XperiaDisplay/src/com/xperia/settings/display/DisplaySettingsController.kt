/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.Context

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import com.xperia.settings.preferences.SecureSettingSwitchPreference

class DisplaySettingsController(context: Context) {
    init {
        DisplayModeInitializer.ensureInitialized(context)
    }

    val creatorModeUtils: CreatorModeUtils = CreatorModeUtils.get(context)
    val xrealityModeUtils: XRealityModeUtils = XRealityModeUtils(context)

    private var suppressPreferenceChange = false

    fun bind(fragment: PreferenceFragmentCompat) {
        val creatorModePreference = fragment.findPreference<SecureSettingSwitchPreference>(CREATOR_MODE_KEY)!!
        creatorModePreference.isChecked = creatorModeUtils.isEnabled
        creatorModePreference.onPreferenceChangeListener = fragment as Preference.OnPreferenceChangeListener

        fragment.findPreference<SecureSettingSwitchPreference>(XREALITY_MODE_KEY)?.let {
            it.isChecked = xrealityModeUtils.isEnabled
            it.onPreferenceChangeListener = fragment as Preference.OnPreferenceChangeListener
        }
    }

    fun onPreferenceChange(
        fragment: PreferenceFragmentCompat,
        preference: Preference,
        newValue: Any?,
    ): Boolean {
        if (suppressPreferenceChange) return true
        val enabled = newValue as Boolean
        when (preference.key) {
            CREATOR_MODE_KEY -> {
                if (enabled) {
                    val hadXrEnabled = xrealityModeUtils.isEnabled
                    if (!xrealityModeUtils.setMode(false)) return false
                    if (hadXrEnabled) {
                        setSwitchChecked(fragment, XREALITY_MODE_KEY, false)
                    }
                }
                if (!creatorModeUtils.setMode(enabled)) return false
            }
            XREALITY_MODE_KEY -> {
                if (enabled && creatorModeUtils.isEnabled) {
                    if (!creatorModeUtils.setMode(false)) return false
                    setSwitchChecked(fragment, CREATOR_MODE_KEY, false)
                }
                if (!xrealityModeUtils.setMode(enabled)) return false
            }
        }
        return true
    }

    private fun setSwitchChecked(fragment: PreferenceFragmentCompat, key: String, checked: Boolean) {
        suppressPreferenceChange = true
        try {
            fragment.findPreference<SecureSettingSwitchPreference>(key)?.isChecked = checked
        } finally {
            suppressPreferenceChange = false
        }
    }
}
