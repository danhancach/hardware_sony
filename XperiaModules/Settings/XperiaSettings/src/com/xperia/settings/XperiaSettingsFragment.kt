/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2022 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings

import android.os.Bundle

import com.android.settingslib.widget.SettingsBasePreferenceFragment

import com.xperia.settings.R
import com.xperia.settings.XperiaSettingsPackage

class XperiaSettingsFragment : SettingsBasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.xperia_settings, rootKey)

        val xperiaSettingsPackage = XperiaSettingsPackage(this)
        xperiaSettingsPackage.setupDisplaySettings()
        xperiaSettingsPackage.setupAudioSettings()
        xperiaSettingsPackage.setupBatterySettings()
        xperiaSettingsPackage.setupCpuSettings()
        xperiaSettingsPackage.setupExtMonSettings()
        xperiaSettingsPackage.setupUSBASettings()
        xperiaSettingsPackage.setupDSMSettings()
        xperiaSettingsPackage.setupACCUISettings()
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.app_name)
    }
}
