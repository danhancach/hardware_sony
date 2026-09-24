/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.Context
import android.os.UserHandle
import android.provider.Settings

/** Centralized persistence for the mutually exclusive display modes. */
object DisplayModeSettings {
    const val CREATOR_ENABLE = "cm_enable"
    const val CREATOR_SWITCH = "switchCreatorMode"
    const val XREALITY_ENABLE = "xr_enable"
    const val XREALITY_SWITCH = "switchXRealityMode"

    fun isCreatorEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        return getBoolean(resolver, CREATOR_SWITCH) || getBoolean(resolver, CREATOR_ENABLE)
    }

    fun isXRealityEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        return getBoolean(resolver, XREALITY_SWITCH) || getBoolean(resolver, XREALITY_ENABLE)
    }

    fun setCreatorEnabled(context: Context, enabled: Boolean) {
        val resolver = context.contentResolver
        putBoolean(resolver, CREATOR_ENABLE, enabled)
        putBoolean(resolver, CREATOR_SWITCH, enabled)
    }

    fun setXRealityEnabled(context: Context, enabled: Boolean) {
        val resolver = context.contentResolver
        putBoolean(resolver, XREALITY_ENABLE, enabled)
        putBoolean(resolver, XREALITY_SWITCH, enabled)
    }

    private fun getBoolean(resolver: android.content.ContentResolver, key: String): Boolean {
        return Settings.Secure.getIntForUser(resolver, key, 0, UserHandle.USER_CURRENT) != 0
    }

    private fun putBoolean(
        resolver: android.content.ContentResolver,
        key: String,
        enabled: Boolean,
    ) {
        Settings.Secure.putIntForUser(
            resolver,
            key,
            if (enabled) 1 else 0,
            UserHandle.USER_CURRENT,
        )
    }
}
