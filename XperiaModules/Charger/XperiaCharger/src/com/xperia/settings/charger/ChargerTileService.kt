/*
 * Copyright (C) 2025 XperiaLabs Project
 * Copyright (C) 2022 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ChargerTileService : TileService() {
    private lateinit var chargerUtils: ChargerUtils

    override fun onCreate() {
        super.onCreate()
        chargerUtils = ChargerUtils(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        chargerUtils.isHSPCEnabled = !chargerUtils.isHSPCEnabled
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val active = chargerUtils.isHSPCEnabled
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.charger_hs_tile)
        tile.subtitle = getString(
            if (active) R.string.charger_hs_tile_on else R.string.charger_hs_tile_off
        )
        tile.updateTile()
    }
}
