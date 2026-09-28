/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

/**
 * Khoang thong ke — moi range co bo dem rieng, reset theo moc su kien:
 * - SINCE_START: tu luc reset toan bo (menu Reset)
 * - SINCE_FULL: tu lan pin 100% gan nhat (edge + offline level<100->100)
 * - SINCE_NOW: tu luc user chon "Ke tu bay gio" (reset thu cong)
 * - SINCE_UNPLUG: tu lan rut sac gan nhat (backend; UI spinner khong expose)
 *
 * Thu tu enum giu ordinal UI cu (START=0, FULL=1, NOW=2) — UNPLUG them cuoi.
 * Du lieu luu SharedPreferences theo prefsPrefix, doc lap Activity/Service.
 */
enum class StatsRange {
    SINCE_START,
    SINCE_FULL,
    SINCE_NOW,
    SINCE_UNPLUG;

    val prefsPrefix: String
        get() = when (this) {
            SINCE_START -> "start_"
            SINCE_FULL -> "full_"
            SINCE_NOW -> "now_"
            SINCE_UNPLUG -> "unplug_"
        }

    /** Spinner chi 3 muc — UNPLUG chi backend. */
    fun asUiRange(): StatsRange =
        if (this == SINCE_UNPLUG) SINCE_START else this
}
