/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.res.Configuration
import android.os.Bundle

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class DisplaySettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.display_settings_title)
        showFragmentForOrientation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        title = getString(R.string.display_settings_title)
        showFragmentForOrientation()
    }

    private fun showFragmentForOrientation() {
        val contentFrame = com.android.settingslib.collapsingtoolbar.R.id.content_frame
        val tag = currentFragmentTag()
        val existing = supportFragmentManager.findFragmentById(contentFrame)
        if (existing?.tag == tag) {
            return
        }

        val fragment = if (isLandscape()) {
            DisplaySettingsLandscapeFragment()
        } else {
            DisplaySettingsFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(contentFrame, fragment, tag)
            .commitNow()
    }

    private fun currentFragmentTag(): String {
        return if (isLandscape()) LANDSCAPE_TAG else PORTRAIT_TAG
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    companion object {
        private const val PORTRAIT_TAG = "DisplaySettingsPortrait"
        private const val LANDSCAPE_TAG = "DisplaySettingsLandscape"
    }
}
