/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.charger

import android.os.Bundle

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class ChargerSettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.charger_settings_title)

        val contentFrame = com.android.settingslib.collapsingtoolbar.R.id.content_frame
        if (supportFragmentManager.findFragmentById(contentFrame) == null) {
            supportFragmentManager.beginTransaction()
                .replace(contentFrame, ChargerSettingsFragment())
                .commit()
        }
    }
}
