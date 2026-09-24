/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.audio

import android.os.Bundle

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class AudioSettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.audio_menu_title)

        val contentFrame = com.android.settingslib.collapsingtoolbar.R.id.content_frame
        if (supportFragmentManager.findFragmentById(contentFrame) == null) {
            supportFragmentManager.beginTransaction()
                .replace(contentFrame, AudioSettingsFragment(), TAG)
                .commit()
        }
    }

    companion object {
        private const val TAG = "AudioSettingsActivity"
    }
}
