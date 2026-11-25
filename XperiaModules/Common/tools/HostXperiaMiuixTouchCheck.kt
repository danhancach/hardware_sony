/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui.tools

import com.xperia.settings.ui.XperiaScrollPhase
import com.xperia.settings.ui.XperiaStretchMath
import com.xperia.settings.ui.XperiaTouchAxisOwnership
import com.xperia.settings.ui.XperiaTouchAxisOwnership.Axis
import com.xperia.settings.ui.XperiaTouchAxisOwnership.Event
import kotlin.math.abs

object HostXperiaMiuixTouchCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        verifyStretchMath()
        verifyScrollPhases()
        verifyTouchAxisOwnership()
        println("PASS HostXperiaMiuixTouchCheck (StretchMath, ScrollPhase, TouchAxisOwnership)")
    }

    private fun verifyStretchMath() {
        val g = 1000f
        check(abs(XperiaStretchMath.stretchVisual(g, g) - g / 3f) < 1e-3f)
        check(abs(XperiaStretchMath.stretchVisual(-g, g) + g / 3f) < 1e-3f)

        for (f in floatArrayOf(50f, 350f, 920f, -180f, -750f)) {
            val e = XperiaStretchMath.stretchVisual(f, g)
            val inv = XperiaStretchMath.inverseStretch(e, g)
            check(abs(inv - f) < 1.5f)
        }

        // B1 reacquire trong vung bound va ngoai vung unbound (|E| > G/3)
        val boundReacq = XperiaStretchMath.reacquireAfterInterrupt(150f, g)
        check(!boundReacq.unbound && abs(boundReacq.visualE - 150f) < 1e-3f)
        check(abs(XperiaStretchMath.stretchVisual(boundReacq.rawF, g) - 150f) < 1.5f)

        val unboundReacq = XperiaStretchMath.reacquireAfterInterrupt(450f, g)
        check(unboundReacq.unbound && unboundReacq.rawF == g && unboundReacq.visualE == 450f)

        // Unbound keo giam dan ve trong vung bound (<= G/3)
        val backToBound = XperiaStretchMath.applyDelta(unboundReacq, -200f, g)
        check(!backToBound.unbound && abs(backToBound.visualE - 250f) < 1.5f)

        // Reverse release chi giai toa phan nguoc chieu va khong vuot zero
        val tension200 = XperiaStretchMath.TensionState(
            rawF = 200f,
            visualE = XperiaStretchMath.stretchVisual(200f, g),
        )
        val (sameDirState, sameDirConsumed) = XperiaStretchMath.reverseRelease(tension200, 80f, g)
        check(sameDirConsumed == 0f && sameDirState == tension200)

        val (partialRev, partialConsumed) = XperiaStretchMath.reverseRelease(tension200, -120f, g)
        check(abs(partialConsumed - (-120f)) < 1e-3f && abs(partialRev.rawF - 80f) < 1e-3f)

        val (overRev, overConsumed) = XperiaStretchMath.reverseRelease(tension200, -350f, g)
        check(overRev.rawF == 0f && overRev.visualE == 0f && abs(overConsumed - (-200f)) < 1e-3f)

        // Chu ky od3 settle theo nguong 5000px/s
        check(XperiaStretchMath.settlePeriodSeconds(4999f) == 0.4)
        check(XperiaStretchMath.settlePeriodSeconds(-5001f) == 0.55)

        // Non-oscillating zero-cross stop
        check(XperiaStretchMath.shouldClearSettle(pos = -1.5, vel = -20.0, prevPos = 2.0))
        check(XperiaStretchMath.shouldClearSettle(pos = 0.4, vel = 0.5, prevPos = 0.9))
        check(!XperiaStretchMath.shouldClearSettle(pos = 8.0, vel = -10.0, prevPos = 10.0))

        val (finalPos, steps) = XperiaStretchMath.settleUntilZero(120.0, 0.0, 0.4, scale = 1.0)
        check(finalPos == 0.0 && steps > 0)
        val (snapPos, snapSteps) = XperiaStretchMath.settleUntilZero(120.0, 500.0, 0.4, scale = 0.0)
        check(snapPos == 0.0 && snapSteps == 0)
    }

    private fun verifyScrollPhases() {
        val g = 1000f
        val topTension = XperiaStretchMath.stretchVisual(150f, g)

        // PreScroll: giai R0 truoc, roi moi thu gon header; khong tao stretch moi o Pre
        val capsWithTension = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 500f,
            tensionE = topTension,
            boundG = g,
        )
        val pre = XperiaScrollPhase.preScroll(-200f, capsWithTension)
        check(abs(pre.tensionConsumed - (-150f)) < 1e-3f)
        check(abs(pre.headerConsumed - (-50f)) < 1e-3f)
        check(pre.toChild == 0f)
        check(pre.nextTensionState.visualE == 0f)

        // PreScroll khong tao stretch khi header da thu gon xong
        val capsCollapsed = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = -100,
            totalScrollRange = 100,
            contentUpRemaining = 200f,
            contentDownRemaining = 200f,
            tensionE = 0f,
            boundG = g,
        )
        val prePassThrough = XperiaScrollPhase.preScroll(-60f, capsCollapsed)
        check(prePassThrough.tensionConsumed == 0f && prePassThrough.headerConsumed == 0f)
        check(prePassThrough.toChild == -60f)

        // PostScroll: mo rong header truoc khi dan hoi dinh
        val capsAtTopCollapsed = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = -40,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 400f,
            tensionE = 0f,
            boundG = g,
        )
        val postExpand = XperiaScrollPhase.postScroll(80f, capsAtTopCollapsed)
        check(abs(postExpand.headerConsumed - 40f) < 1e-3f)
        check(postExpand.stretchConsumed == 0f)

        val capsExpandedTop = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 400f,
            tensionE = 0f,
            boundG = g,
        )
        val postTopStretch = XperiaScrollPhase.postScroll(40f, capsExpandedTop)
        check(postTopStretch.headerConsumed == 0f && abs(postTopStretch.stretchConsumed - 40f) < 1e-3f)

        // Trang ngan (!canContentScroll) cho phep stretch
        val capsShort = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 0f,
            tensionE = 0f,
            boundG = g,
        )
        val postShort = XperiaScrollPhase.postScroll(-35f, capsShort)
        check(abs(postShort.stretchConsumed - (-35f)) < 1e-3f)

        // Fling F0 / I0
        check(abs(XperiaScrollPhase.flingPreConsumedY(40f, 120f) - 120f) < 1e-3f)
        check(abs(XperiaScrollPhase.flingPreConsumedY(40f, -120f) - (-120f / 2.13333f)) < 1e-3f)
        check(abs((XperiaScrollPhase.flingSettleVelocity(10f, 46f) ?: 0f) - (46f / 1.53333f)) < 1e-3f)
        check(XperiaScrollPhase.flingSettleVelocity(10f, 0f) == 0f)
        check(XperiaScrollPhase.flingSettleVelocity(0f, 0f) == null)

        // Hoi quy 1: keo cham 1px/frame qua postScroll phai tich luy qua tensionRawF
        var slowState = XperiaStretchMath.TensionState()
        repeat(10) {
            val slowCaps = XperiaScrollPhase.capsFromViewMetrics(
                appBarTop = 0,
                totalScrollRange = 100,
                contentUpRemaining = 0f,
                contentDownRemaining = 400f,
                tensionE = slowState.visualE,
                boundG = g,
                tensionRawF = slowState.rawF,
                tensionUnbound = slowState.unbound,
            )
            slowState = XperiaScrollPhase.postScroll(1f, slowCaps).nextTensionState
        }
        check(abs(slowState.rawF - 10f) < 1e-3f && slowState.visualE > 9f)

        // Hoi quy 2: giai toa subpixel (rawF = 0.8f, visualE < 1px) qua preScroll phai ve 0
        val subpixelE = XperiaStretchMath.stretchVisual(0.8f, g)
        val subpixelCaps = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 400f,
            tensionE = subpixelE,
            boundG = g,
            tensionRawF = 0.8f,
        )
        val subpixelRev = XperiaScrollPhase.preScroll(-0.8f, subpixelCaps)
        check(abs(subpixelRev.tensionConsumed - (-0.8f)) < 1e-3f)
        check(subpixelRev.nextTensionState.rawF == 0f && subpixelRev.nextTensionState.visualE == 0f)

        // Hoi quy 3: trang ngan nhung header dang dong -> !headerFullyExpanded && !canTopStretch
        val capsShortCollapsed = XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = -100,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 0f,
            tensionE = 0f,
            boundG = g,
        )
        check(!capsShortCollapsed.headerFullyExpanded && !capsShortCollapsed.canTopStretch)
    }

    private fun verifyTouchAxisOwnership() {
        val cfg = XperiaTouchAxisOwnership.Config(touchSlopPx = 16f, dominantRatio = 1.0f)

        // 1. Vuot doc dai kem troi ngang nhe -> giu Vertical
        val verticalWithDrift = XperiaTouchAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f, hasHorizontalTarget = true),
                Event.Move(0, 108f, 150f),
                Event.Move(0, 125f, 240f),
                Event.Move(0, 145f, 400f),
            ),
            cfg,
        )
        check(verticalWithDrift.axis == Axis.Vertical)
        check(XperiaTouchAxisOwnership.shouldReleaseNestedHorizontalParent(verticalWithDrift))
        check(!XperiaTouchAxisOwnership.shouldBlockVerticalParent(verticalWithDrift))

        // 2. Hoa |dx| == |dy| -> uu tien Vertical
        val tieState = XperiaTouchAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f, hasHorizontalTarget = true),
                Event.Move(0, 30f, 30f),
            ),
            cfg,
        )
        check(tieState.axis == Axis.Vertical)

        // 3. Horizontal Takeover (kc2AngleCommit 1.5x) vuot qua nhich doc ban dau
        val initialDown = XperiaTouchAxisOwnership.down(
            XperiaTouchAxisOwnership.reset(),
            0,
            100f,
            100f,
            hasHorizontalTarget = true,
        )
        val afterVerticalJitter = XperiaTouchAxisOwnership.move(initialDown, 0, 104f, 124f, cfg)
        check(afterVerticalJitter.axis == Axis.Vertical)
        val afterHorizontalTakeover = XperiaTouchAxisOwnership.move(afterVerticalJitter, 0, 180f, 130f, cfg)
        check(afterHorizontalTakeover.axis == Axis.Horizontal)
        check(
            XperiaTouchAxisOwnership.shouldClearVerticalTension(
                afterVerticalJitter,
                afterHorizontalTakeover,
            ),
        )
        // Sau takeover, frame tiep theo khong ban clear lap lai
        val afterContinuedHoriz = XperiaTouchAxisOwnership.move(afterHorizontalTakeover, 0, 220f, 132f, cfg)
        check(
            !XperiaTouchAxisOwnership.shouldClearVerticalTension(
                afterHorizontalTakeover,
                afterContinuedHoriz,
            ),
        )

        // 4. childHorizontalClaim bao ve Slider qua keo ngang, dao chieu va pointer handoff
        val sliderState = XperiaTouchAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 200f, childHorizontalClaim = true),
                Event.Move(0, 190f, 205f),
                Event.Move(0, 50f, 198f),
                Event.PointerUp(0, successorId = 1, successorX = 50f, successorY = 198f),
                Event.Move(1, 200f, 202f),
            ),
            cfg,
        )
        check(sliderState.active && sliderState.pointerId == 1)
        check(sliderState.childHorizontalClaim && sliderState.axis == Axis.Horizontal)
        check(XperiaTouchAxisOwnership.shouldBlockVerticalParent(sliderState))
        check(!XperiaTouchAxisOwnership.shouldReleaseNestedHorizontalParent(sliderState))

        // 5. POINTER_UP khi Undecided rebase toa do cho pointer ke tiep
        val undecidedTransfer = XperiaTouchAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f, hasHorizontalTarget = true),
                Event.Move(0, 4f, 4f),
                Event.PointerUp(0, successorId = 2, successorX = 50f, successorY = 50f),
                Event.Move(2, 54f, 110f),
            ),
            cfg,
        )
        check(undecidedTransfer.pointerId == 2 && undecidedTransfer.axis == Axis.Vertical)

        // 6. Tap binh thuong khong ep dung fling list khi khong co tension hoac widget ngang
        check(!XperiaTouchAxisOwnership.shouldStopListFlingOnDown(hasTension = false, touchedHorizontalWidget = false))
        check(XperiaTouchAxisOwnership.shouldStopListFlingOnDown(hasTension = true, touchedHorizontalWidget = false))
        check(XperiaTouchAxisOwnership.shouldStopListFlingOnDown(hasTension = false, touchedHorizontalWidget = true))
    }
}
