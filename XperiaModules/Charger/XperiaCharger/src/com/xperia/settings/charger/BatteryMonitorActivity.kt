/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.os.Bundle
import android.widget.Toolbar
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class BatteryMonitorActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.battery_monitor_title)

        // Popup overflow (3 cham) bo goc giong the card
        findViewById<Toolbar>(com.android.settingslib.collapsingtoolbar.R.id.action_bar)
            ?.popupTheme = R.style.BatteryMonitorPopupOverlay

        val contentFrame = com.android.settingslib.collapsingtoolbar.R.id.content_frame
        if (supportFragmentManager.findFragmentById(contentFrame) == null) {
            supportFragmentManager.beginTransaction()
                .replace(contentFrame, BatteryMonitorFragment())
                .commit()
        }
    }
}
