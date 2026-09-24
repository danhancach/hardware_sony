/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.audio

import android.os.Bundle

import com.android.settingslib.widget.SettingsBasePreferenceFragment

import com.xperia.settings.audio.R

class AudioSettingsFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.audio_settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.audio_menu_title)
    }
}
