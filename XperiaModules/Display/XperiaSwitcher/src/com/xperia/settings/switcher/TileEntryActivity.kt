/*
 * Copyright (C) 2021 Chaldeaprjkt
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.switcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log

public class TileEntryActivity: Activity() {
    private val TAG: String = "TileEntryActivity"
    private val REFRESH_TILE: String = "com.xperia.settings.switcher.RefreshRateTileService";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceClass: ComponentName? =
            intent?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
        when (sourceClass?.className) {
            REFRESH_TILE -> {
                val intent: Intent = Intent()
                intent.setComponent(
                    ComponentName("com.android.settings",
                        "com.android.settings.Settings\$RefreshRateSettingsActivity"));
                openActivitySafely(intent);
            }
            else -> {
                finish()
            }
        }
    }

    fun openActivitySafely(dest: Intent) {
        try {
            dest.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            finish()
            startActivity(dest)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity found for " + dest)
            finish()
        }
    }
}
