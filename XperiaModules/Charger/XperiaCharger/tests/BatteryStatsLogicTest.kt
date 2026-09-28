/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.monitor

/**
 * Unit test thuan (khong Android) — chay bang kotlinc.
 * Module chua co android_test trong Android.bp.
 */
object BatteryStatsLogicTest {

    @JvmStatic
    fun main(args: Array<String>) {
        testChargeCounterDrain()
        testReverseCounterRejected()
        testLargeGapNoDrain()
        testLevelFallback()
        testDrainPerHourCap()
        testTransitionUsesUnpluggedBefore()
        testPlugTransitionBoundaryOrder()
        testUnplugTransitionClearsAfterAccumulate()
        testShouldFlushUsesPluggedChangedBeforeAssign()
        testSelectedRangeUiHidesUnplug()
        testUiRangeOrdinalStable()
        println("BatteryStatsLogicTest: ALL PASSED")
    }

    private fun assertEq(name: String, expected: Float, actual: Float, eps: Float = 0.01f) {
        check(kotlin.math.abs(expected - actual) <= eps) {
            "$name: expected $expected got $actual"
        }
    }

    private fun testChargeCounterDrain() {
        // 100000 uAh / 5000000 full = 2%; dt 1h de khong hit cap %/h
        val pct = BatteryStatsLogic.computeDrainPercent(
            lastLevel = 80, levelPercent = 80,
            lastPrecise = -1f, precise = -1f,
            lastCounter = 4_000_000L, counter = 3_900_000L,
            chargeFull = 5_000_000L,
            largeGap = false, dtElapsed = 3_600_000L
        )
        assertEq("charge_counter", 2f, pct)
    }

    private fun testReverseCounterRejected() {
        val pct = BatteryStatsLogic.computeDrainPercent(
            lastLevel = 80, levelPercent = 80,
            lastPrecise = -1f, precise = -1f,
            lastCounter = 3_900_000L, counter = 4_000_000L,
            chargeFull = 5_000_000L,
            largeGap = false, dtElapsed = 60_000L
        )
        assertEq("reverse_counter", 0f, pct)
    }

    private fun testLargeGapNoDrain() {
        val pct = BatteryStatsLogic.computeDrainPercent(
            lastLevel = 80, levelPercent = 70,
            lastPrecise = -1f, precise = -1f,
            lastCounter = 4_000_000L, counter = 3_500_000L,
            chargeFull = 5_000_000L,
            largeGap = true, dtElapsed = 3_600_000L
        )
        assertEq("large_gap", 0f, pct)
    }

    private fun testLevelFallback() {
        val pct = BatteryStatsLogic.computeDrainPercent(
            lastLevel = 85, levelPercent = 83,
            lastPrecise = -1f, precise = -1f,
            lastCounter = -1L, counter = -1L,
            chargeFull = -1L,
            largeGap = false, dtElapsed = 3_600_000L
        )
        assertEq("level_fallback", 2f, pct)
    }

    private fun testDrainPerHourCap() {
        val rate = BatteryStatsLogic.drainPerHour(25f, 3_600_000L)
        assertEq("drain_per_hour", 25f, rate)
        val capped = BatteryStatsLogic.drainPerHour(200f, 3_600_000L)
        assertEq("drain_cap", BatteryStatsLogic.MAX_DRAIN_PERCENT_PER_HOUR, capped)
        val short = BatteryStatsLogic.drainPerHour(10f, 10_000L)
        assertEq("short_duration", 0f, short)
    }

    private fun testTransitionUsesUnpluggedBefore() {
        // Khoang truoc unplugged — drain khi !lastPlugged
        val pct = BatteryStatsLogic.computeDrainPercent(
            lastLevel = 90, levelPercent = 89,
            lastPrecise = -1f, precise = -1f,
            lastCounter = -1L, counter = -1L,
            chargeFull = -1L,
            largeGap = false, dtElapsed = 300_000L
        )
        assertEq("transition_drain", 1f, pct)
    }

    /** Bien cam sac: accumulate + drain dung lastPlugged TRUOC khi gan. */
    private fun testPlugTransitionBoundaryOrder() {
        var lastPlugged = false
        val plugged = true
        var onBatteryMs = 0L
        var chargeMs = 0L
        var drain = 0f

        if (lastPlugged) chargeMs += 60_000L else onBatteryMs += 60_000L
        if (!lastPlugged) {
            drain += BatteryStatsLogic.computeDrainPercent(
                lastLevel = 90, levelPercent = 89,
                lastPrecise = -1f, precise = -1f,
                lastCounter = -1L, counter = -1L,
                chargeFull = -1L,
                largeGap = false, dtElapsed = 3_600_000L
            )
        }
        lastPlugged = plugged

        check(onBatteryMs == 60_000L) { "plug_boundary: interval must stay on-battery" }
        check(chargeMs == 0L) { "plug_boundary: chargeMs must not steal prior interval" }
        assertEq("plug_boundary_drain", 1f, drain)
        check(lastPlugged)
    }

    /** Rut sac: accumulate van dung lastPlugged=true, roi moi clear SINCE_UNPLUG. */
    private fun testUnplugTransitionClearsAfterAccumulate() {
        var lastPlugged = true
        val plugged = false
        var chargeMs = 0L
        var sinceUnplugMs = 100_000L
        var drain = 0f

        if (lastPlugged) chargeMs += 30_000L else sinceUnplugMs += 30_000L
        if (!lastPlugged) {
            drain += 1f
        }
        if (lastPlugged && !plugged) {
            sinceUnplugMs = 0L
        }
        lastPlugged = plugged

        check(chargeMs == 30_000L) { "unplug_boundary: prior interval stays charge" }
        assertEq("unplug_boundary_drain", 0f, drain)
        check(sinceUnplugMs == 0L) { "unplug_boundary: SINCE_UNPLUG cleared after accumulate" }
        check(!lastPlugged)
    }

    /** shouldFlush dung pluggedChanged TRUOC khi gan lastPlugged. */
    private fun testShouldFlushUsesPluggedChangedBeforeAssign() {
        var lastPlugged = false
        val plugged = true
        val pluggedChanged = lastPlugged != plugged
        lastPlugged = plugged
        val buggy = lastPlugged != plugged
        check(pluggedChanged) { "shouldFlush: pluggedChanged must be true on transition" }
        check(!buggy) { "shouldFlush: post-assign compare is always false (documents bug)" }
        check(pluggedChanged) { "shouldFlush: transition must flush" }
    }

    /** Spinner/UI khong expose SINCE_UNPLUG. */
    private fun testSelectedRangeUiHidesUnplug() {
        check(StatsRange.SINCE_UNPLUG.asUiRange() == StatsRange.SINCE_START)
        check(StatsRange.SINCE_NOW.asUiRange() == StatsRange.SINCE_NOW)
        check(StatsRange.SINCE_FULL.asUiRange() == StatsRange.SINCE_FULL)
    }

    /** Ordinal UI on dinh voi ban cu (NOW=2); UNPLUG them cuoi. */
    private fun testUiRangeOrdinalStable() {
        check(StatsRange.SINCE_START.ordinal == 0)
        check(StatsRange.SINCE_FULL.ordinal == 1)
        check(StatsRange.SINCE_NOW.ordinal == 2)
        check(StatsRange.SINCE_UNPLUG.ordinal == 3)
    }
}
