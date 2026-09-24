/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

/**
 * Khoang thong ke: since start / last full / since now.
 */
enum class StatsRange {
    SINCE_START,
    SINCE_FULL,
    SINCE_NOW;

    val prefsPrefix: String
        get() = when (this) {
            SINCE_START -> "start_"
            SINCE_FULL -> "full_"
            SINCE_NOW -> "now_"
        }
}
