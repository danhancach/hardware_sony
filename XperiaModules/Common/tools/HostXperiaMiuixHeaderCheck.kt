/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui.tools

import com.xperia.settings.ui.XperiaAppBarScrollGate
import com.xperia.settings.ui.XperiaMiuixTitleMotion
import kotlin.math.PI
import kotlin.math.abs

object HostXperiaMiuixHeaderCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        verifyMiuixFolmeMotion()
        verifyTitleGeometryLtrAndRtl()
        verifyAppBarScrollGate()
        println("PASS HostXperiaMiuixHeaderCheck (Folme Spring, LTR/RTL geometry, AppBar fling gate)")
    }

    private fun verifyMiuixFolmeMotion() {
        check(XperiaMiuixTitleMotion.collapseFraction(0f, 100f) == 0f)
        check(XperiaMiuixTitleMotion.collapseFraction(-50f, 100f) == 0.5f)
        check(XperiaMiuixTitleMotion.collapseFraction(-100f, 100f) == 1f)
        check(XperiaMiuixTitleMotion.collapseFraction(-150f, 100f) == 1f)
        check(XperiaMiuixTitleMotion.collapseFraction(-50f, 0f) == 0f)

        // Nguong 1/3 kich hoat tieu de nho
        check(!XperiaMiuixTitleMotion.isSmallTitleVisible(0f))
        check(!XperiaMiuixTitleMotion.isSmallTitleVisible(0.2f))
        check(!XperiaMiuixTitleMotion.isSmallTitleVisible(0.33f))
        check(XperiaMiuixTitleMotion.isSmallTitleVisible(1f / 3f))
        check(XperiaMiuixTitleMotion.isSmallTitleVisible(0.5f))
        check(XperiaMiuixTitleMotion.isSmallTitleVisible(1f))

        // Cong thuc mo tieu de lon: mo het trong 1/3 hanh trinh dau tien
        check(XperiaMiuixTitleMotion.largeTitleAlpha(0f) == 1f)
        check(abs(XperiaMiuixTitleMotion.largeTitleAlpha(1f / 6f) - 0.5f) < 0.001f)
        check(XperiaMiuixTitleMotion.largeTitleAlpha(1f / 3f) == 0f)
        check(XperiaMiuixTitleMotion.largeTitleAlpha(1f) == 0f)

        // Cong thuc Folme Spring
        val expectedShowK = ((2.0 * PI / 0.3) * (2.0 * PI / 0.3)).toFloat()
        val expectedHideK = ((2.0 * PI / 0.15) * (2.0 * PI / 0.15)).toFloat()
        check(abs(XperiaMiuixTitleMotion.StiffnessShow - expectedShowK) < 0.01f)
        check(abs(XperiaMiuixTitleMotion.StiffnessHide - expectedHideK) < 0.01f)
        check(abs(XperiaMiuixTitleMotion.StiffnessHide - XperiaMiuixTitleMotion.StiffnessShow * 4f) < 0.01f)

        // Hoi tu alpha Show (0.3s) va Hide (0.15s)
        var aVal = 0f
        var aVel = 0f
        var showSteps = 0
        while (aVal < 0.99f && showSteps < 120) {
            val s = XperiaMiuixTitleMotion.alphaStep(aVal, aVel, visible = true, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
            showSteps++
        }
        check(aVal >= 0.99f)
        repeat(60) {
            val s = XperiaMiuixTitleMotion.alphaStep(aVal, aVel, visible = true, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
        }
        check(aVal == 1f && aVel == 0f)

        var hideSteps = 0
        while (aVal > 0.01f && hideSteps < 120) {
            val s = XperiaMiuixTitleMotion.alphaStep(aVal, aVel, visible = false, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
            hideSteps++
        }
        check(aVal <= 0.01f)
        check(hideSteps < showSteps)
        repeat(60) {
            val s = XperiaMiuixTitleMotion.alphaStep(aVal, aVel, visible = false, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
        }
        check(aVal == 0f && aVel == 0f)

        // TranslationY: 20px -> 0px -> 20px
        var yVal = XperiaMiuixTitleMotion.TransitionDistancePx
        var yVel = 0f
        repeat(60) {
            val s = XperiaMiuixTitleMotion.translationYStep(yVal, yVel, visible = true, 1f / 60f)
            yVal = s.value
            yVel = s.velocity
        }
        check(yVal == 0f && yVel == 0f)

        repeat(60) {
            val s = XperiaMiuixTitleMotion.translationYStep(yVal, yVel, visible = false, 1f / 60f)
            yVal = s.value
            yVel = s.velocity
        }
        check(yVal == XperiaMiuixTitleMotion.TransitionDistancePx && yVel == 0f)

        // Dao chieu giua chung van giu van toc dong hoc
        var revVal = 0f
        var revVel = 0f
        repeat(5) {
            val s = XperiaMiuixTitleMotion.alphaStep(revVal, revVel, visible = true, 1f / 60f)
            revVal = s.value
            revVel = s.velocity
        }
        check(revVal > 0f && revVel > 0f)
        repeat(60) {
            val s = XperiaMiuixTitleMotion.alphaStep(revVal, revVel, visible = false, 1f / 60f)
            revVal = s.value
            revVel = s.velocity
        }
        check(revVal == 0f && revVel == 0f)
    }

    private fun verifyTitleGeometryLtrAndRtl() {
        // LTR co nut Back 40dp + gap 22dp (khong cong don titleMarginStartPx)
        check(
            XperiaMiuixTitleMotion.collapsedTitleStartX(
                toolbarLeftPx = 8f,
                navCenterXPx = 36f,
                hasNavigation = true,
                backIconSizePx = 40f,
                gapPx = 22f,
                contentInsetStartPx = 72f,
                titleMarginStartPx = 16f,
            ) == 78f,
        )
        // LTR fallback khi khong co nut Back
        check(
            XperiaMiuixTitleMotion.collapsedTitleStartX(
                toolbarLeftPx = 8f,
                navCenterXPx = 0f,
                hasNavigation = false,
                backIconSizePx = 40f,
                gapPx = 22f,
                contentInsetStartPx = 72f,
                titleMarginStartPx = 16f,
            ) == 96f,
        )

        // LTR gioi han khi co ActionMenuView o cuoi toolbar
        val ltrMenuBounds = XperiaMiuixTitleMotion.collapsedTitleHorizontalBounds(
            containerWidthPx = 1080f,
            toolbarLeftPx = 8f,
            toolbarRightPx = 1080f,
            navCenterXPx = 44f,
            hasNavigation = true,
            backIconSizePx = 40f,
            gapPx = 22f,
            contentInsetStartPx = 72f,
            titleMarginStartPx = 16f,
            endReservePx = 120f,
            isRtl = false,
        )
        check(ltrMenuBounds.drawStartX == 86f)
        check(ltrMenuBounds.availableWidth == 1080f - 120f - 86f)

        // RTL: mep phai nam ben trai vong back 40dp - gap 22dp, tru endReserve ben trai
        val rtlBounds = XperiaMiuixTitleMotion.collapsedTitleHorizontalBounds(
            containerWidthPx = 1080f,
            toolbarLeftPx = 0f,
            toolbarRightPx = 1072f,
            navCenterXPx = 1036f,
            hasNavigation = true,
            backIconSizePx = 40f,
            gapPx = 22f,
            contentInsetStartPx = 72f,
            titleMarginStartPx = 16f,
            endReservePx = 120f,
            isRtl = true,
        )
        check(rtlBounds.drawStartX == 1036f - 20f - 22f)
        check(rtlBounds.availableWidth == (1036f - 20f - 22f) - 120f)
    }

    private fun verifyAppBarScrollGate() {
        val gate = XperiaAppBarScrollGate()

        // 1. Vuot ngan len + fling (RecyclerView goi start(NON_TOUCH) truoc stop(TOUCH))
        gate.onStartNestedScroll(isTouch = true, acceptedBySuper = true)
        check(gate.shouldAllowNestedPreScroll(isTouch = true, dy = 30))
        gate.onNestedFlingDispatched()
        gate.onStartNestedScroll(isTouch = false, acceptedBySuper = true)
        gate.onStopNestedScroll(isTouch = true)
        check(gate.isTouchFlingActive)
        // Fling huong len (dy > 0) phai tiep tuc thu gon AppBar
        check(gate.shouldAllowNestedPreScroll(isTouch = false, dy = 80))
        // Fling huong xuong giua list (dy < 0) phai bi chan o pre-scroll de khong de tieu de
        check(!gate.shouldAllowNestedPreScroll(isTouch = false, dy = -40))
        // Fling cham dinh list (dyUnconsumed < 0) duoc mo rong AppBar
        check(gate.shouldAllowNestedScroll(isTouch = false, dyUnconsumed = -25))
        gate.onStopNestedScroll(isTouch = false)
        check(!gate.isTouchFlingActive)

        // 2. Truong hop stop(TOUCH) chay truoc start(NON_TOUCH) sau khi dispatch fling
        gate.onStartNestedScroll(isTouch = true, acceptedBySuper = true)
        gate.onNestedFlingDispatched()
        gate.onStopNestedScroll(isTouch = true)
        gate.onStartNestedScroll(isTouch = false, acceptedBySuper = true)
        check(gate.isTouchFlingActive)
        check(gate.shouldAllowNestedPreScroll(isTouch = false, dy = 50))
        gate.onStopNestedScroll(isTouch = false)

        // 3. Cuon non-touch tu ban phim / focus (khong co touch truoc do) -> chan ca pre va post
        gate.onStartNestedScroll(isTouch = false, acceptedBySuper = true)
        check(!gate.isTouchFlingActive)
        check(!gate.shouldAllowNestedPreScroll(isTouch = false, dy = 60))
        check(!gate.shouldAllowNestedPreScroll(isTouch = false, dy = -60))
        check(!gate.shouldAllowNestedScroll(isTouch = false, dyUnconsumed = -60))
        gate.onStopNestedScroll(isTouch = false)

        // 4. Cham tay moi huy co fling cu
        gate.onStartNestedScroll(isTouch = true, acceptedBySuper = true)
        gate.onNestedFlingDispatched()
        gate.onStartNestedScroll(isTouch = false, acceptedBySuper = true)
        check(gate.isTouchFlingActive)
        gate.onStartNestedScroll(isTouch = true, acceptedBySuper = true)
        check(!gate.isTouchFlingActive)

        // 5. Hysteresis chong nay tai vung 33%..49% va huong vuot
        // Vuot len ngan (dy > 0): qua 1/6 (~17%) phai thu gon han, khong bat nguoc ve expanded
        check(!XperiaMiuixTitleMotion.shouldSnapToCollapsed(0.10f, lastScrollDy = 25))
        check(XperiaMiuixTitleMotion.shouldSnapToCollapsed(1f / 6f, lastScrollDy = 25))
        check(XperiaMiuixTitleMotion.shouldSnapToCollapsed(0.35f, lastScrollDy = 25))
        check(XperiaMiuixTitleMotion.shouldSnapToCollapsed(0.45f, lastScrollDy = 25))
        // Vuot xuong tu dinh (dy < 0): nhich nhe < 1/3 tu dinh (fraction > 2/3) van giu collapsed
        check(XperiaMiuixTitleMotion.shouldSnapToCollapsed(0.80f, lastScrollDy = -25))
        // Vuot xuong qua 1/3 tu dinh (fraction <= 2/3) moi bung ra expanded
        check(!XperiaMiuixTitleMotion.shouldSnapToCollapsed(0.60f, lastScrollDy = -25))
        // Neu noi dung ben duoi da cuon khoi dinh -> luon giu collapsed
        check(XperiaMiuixTitleMotion.shouldSnapToCollapsed(0.10f, lastScrollDy = -25, canContentScrollUp = true))

        // 6. Kiem tra consumeSettleOnStop khong settle som khi dang cho fling
        gate.reset()
        gate.onStartNestedScroll(isTouch = true, acceptedBySuper = true)
        gate.recordPreScroll(40)
        gate.onNestedFlingDispatched()
        // stop(TOUCH) truoc start(NON_TOUCH) -> khong settle som
        check(!gate.consumeSettleOnStop(isTouch = true))
        gate.onStartNestedScroll(isTouch = false, acceptedBySuper = true)
        check(gate.lastDirectionDy == 40)
        check(gate.consumeSettleOnStop(isTouch = false))

        // Vuot cham khong fling -> stop(TOUCH) cho phep settle ngay
        gate.reset()
        gate.onStartNestedScroll(isTouch = true, acceptedBySuper = true)
        gate.recordPreScroll(30)
        check(gate.consumeSettleOnStop(isTouch = true))
        gate.reset()
    }
}
