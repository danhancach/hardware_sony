/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2022 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings

import android.os.Bundle

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class XperiaSettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)

        val contentFrame = com.android.settingslib.collapsingtoolbar.R.id.content_frame
        if (supportFragmentManager.findFragmentById(contentFrame) == null) {
            supportFragmentManager.beginTransaction()
                .replace(contentFrame, XperiaSettingsFragment(), TAG)
                .commit()
        }
    }

    companion object {
        private const val TAG = "XperiaSettingsActivity"
    }
}
