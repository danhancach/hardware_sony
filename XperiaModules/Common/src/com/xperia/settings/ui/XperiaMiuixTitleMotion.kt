/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Tinh toan chuyen dong tieu de Miuix Folme Spring (khong phu thuoc Android SDK).
 *
 * Quy chuan theo .docs/ui/lsposed-miuix-rea.md muc 3:
 * - Tieu de lon mo het trong 1/3 hanh trinh cuon dau tien (collapsedFraction * 3f).
 * - Tieu de nho kich hoat khi collapsedFraction * 3f >= 1f.
 * - Folme Spring bat doi xung thoi gian (damping = 1.0f):
 *     Show (cuon len): response = 0.3s -> stiffness = 438.6491f, Y +20px -> 0px, Alpha 0 -> 1.
 *     Hide (cuon xuong): response = 0.15s -> stiffness = 1754.5964f, Y 0px -> +20px, Alpha 1 -> 0.
 */
object XperiaMiuixTitleMotion {
    const val TransitionDistancePx = 20f

    const val ResponseShow = 0.3f
    const val ResponseHide = 0.15f
    const val DampingRatio = 1.0f

    const val StiffnessShow = 438.6491f
    const val StiffnessHide = 1754.5964f
    const val Threshold = 0.001f
    const val CollapseThreshold = 1f / 3f
    const val CollapseSnapUpThreshold = 1f / 6f
    const val ExpandSnapDownThreshold = 2f / 3f

    data class SpringSample(
        val value: Float,
        val velocity: Float,
        val settled: Boolean,
    )

    data class TitleHorizontalBounds(
        val drawStartX: Float,
        val availableWidth: Float,
    )

    /** Tinh ty le thu gon [0..1] tu offset am va tong quang duong cuon. */
    fun collapseFraction(offsetPx: Float, totalScrollRangePx: Float): Float =
        if (totalScrollRangePx > 0f) (abs(offsetPx) / totalScrollRangePx).coerceIn(0f, 1f) else 0f

    /** Tieu de lon mo hoan toan trong 1/3 hanh trinh cuon dau tien. */
    fun largeTitleAlpha(collapseFraction: Float): Float =
        (1f - (collapseFraction * 3f)).coerceIn(0f, 1f)

    /** Chi hien tieu de nho khi tieu de lon da mo het (1/3 cuon). */
    fun isSmallTitleVisible(collapseFraction: Float): Boolean =
        (collapseFraction * 3f) >= 1f

    /**
     * Quyet dinh snap theo huong cuon (hysteresis) de tranh nay nguoc tai vung 33%..49%.
     * - Noi dung ben duoi da cuon khoi dinh -> luon giu thu gon (collapsed).
     * - Dang vuot len (dy > 0): chi can qua 1/6 hanh trinh la thu gon han.
     * - Dang vuot xuong (dy < 0): phai mo qua 1/3 tu dinh (fraction <= 2/3) moi bung tieu de lon.
     */
    fun shouldSnapToCollapsed(
        collapseFraction: Float,
        lastScrollDy: Int,
        canContentScrollUp: Boolean = false,
    ): Boolean {
        if (canContentScrollUp) return true
        return when {
            lastScrollDy > 0 -> collapseFraction >= CollapseSnapUpThreshold
            lastScrollDy < 0 -> collapseFraction > ExpandSnapDownThreshold
            else -> collapseFraction >= CollapseThreshold
        }
    }

    fun stiffness(visible: Boolean): Float =
        if (visible) StiffnessShow else StiffnessHide

    /** Buoc tich phan giai tich cho lo xo can toi han (critically damped, damping = 1). */
    fun springStep(
        value: Float,
        velocity: Float,
        target: Float,
        dtSec: Float,
        stiffness: Float = StiffnessShow,
        threshold: Float = Threshold,
    ): SpringSample {
        if (dtSec <= 0f) {
            return SpringSample(value, velocity, settled = false)
        }
        val omega = sqrt(stiffness.coerceAtLeast(0f))
        val x0 = value - target
        if (abs(x0) < threshold && abs(velocity) < threshold) {
            return SpringSample(target, 0f, settled = true)
        }
        val envelope = exp((-omega * dtSec).toDouble()).toFloat()
        val b = velocity + omega * x0
        val newValue = target + (x0 + b * dtSec) * envelope
        val newVelocity = (velocity - omega * b * dtSec) * envelope
        val settled = abs(newValue - target) < threshold && abs(newVelocity) < threshold
        return if (settled) {
            SpringSample(target, 0f, settled = true)
        } else {
            SpringSample(newValue, newVelocity, settled = false)
        }
    }

    /** Buoc spring cho alpha tieu de nho (0..1). */
    fun alphaStep(
        value: Float,
        velocity: Float,
        visible: Boolean,
        dtSec: Float,
    ): SpringSample {
        val target = if (visible) 1f else 0f
        return springStep(value, velocity, target, dtSec, stiffness(visible), Threshold)
    }

    /** Buoc spring cho translationY tieu de nho (+20px <-> 0px). */
    fun translationYStep(
        value: Float,
        velocity: Float,
        visible: Boolean,
        dtSec: Float,
    ): SpringSample {
        val target = if (visible) 0f else TransitionDistancePx
        return springStep(value, velocity, target, dtSec, stiffness(visible), Threshold)
    }

    /**
     * Tinh vi tri X bat dau chu tieu de nho theo canh quang hoc vong tron nut Back 40dp
     * va khoang ho 22dp (khong cong don titleMarginStartPx khi co nut Back).
     */
    fun collapsedTitleStartX(
        toolbarLeftPx: Float,
        navCenterXPx: Float,
        hasNavigation: Boolean,
        backIconSizePx: Float,
        gapPx: Float,
        contentInsetStartPx: Float,
        titleMarginStartPx: Float,
    ): Float {
        return if (hasNavigation && backIconSizePx > 0f) {
            navCenterXPx + backIconSizePx / 2f + gapPx
        } else {
            toolbarLeftPx + contentInsetStartPx + titleMarginStartPx
        }
    }

    /**
     * Tinh vung ngang cho tieu de nho ho tro ca LTR va RTL, tru san ActionMenuView phia cuoi.
     * Voi RTL, drawStartX la mep phai de chu can sat ben trai nut Back ma khong bi dao nguoc kep.
     */
    fun collapsedTitleHorizontalBounds(
        containerWidthPx: Float,
        toolbarLeftPx: Float,
        toolbarRightPx: Float,
        navCenterXPx: Float,
        hasNavigation: Boolean,
        backIconSizePx: Float,
        gapPx: Float,
        contentInsetStartPx: Float,
        titleMarginStartPx: Float,
        endReservePx: Float,
        isRtl: Boolean,
    ): TitleHorizontalBounds {
        val clampedRight = toolbarRightPx.coerceAtLeast(toolbarLeftPx)
        val clampedLeft = toolbarLeftPx.coerceAtMost(containerWidthPx.coerceAtLeast(toolbarLeftPx))
        if (!isRtl) {
            val startX = collapsedTitleStartX(
                toolbarLeftPx = clampedLeft,
                navCenterXPx = navCenterXPx,
                hasNavigation = hasNavigation,
                backIconSizePx = backIconSizePx,
                gapPx = gapPx,
                contentInsetStartPx = contentInsetStartPx,
                titleMarginStartPx = titleMarginStartPx,
            )
            val endLimit = (clampedRight - endReservePx).coerceAtLeast(startX)
            return TitleHorizontalBounds(
                drawStartX = startX,
                availableWidth = (endLimit - startX).coerceAtLeast(0f),
            )
        }
        val rightAnchor = if (hasNavigation && backIconSizePx > 0f) {
            navCenterXPx - backIconSizePx / 2f - gapPx
        } else {
            clampedRight - contentInsetStartPx - titleMarginStartPx
        }
        val leftLimit = (clampedLeft + endReservePx).coerceAtMost(rightAnchor)
        return TitleHorizontalBounds(
            drawStartX = rightAnchor,
            availableWidth = (rightAnchor - leftLimit).coerceAtLeast(0f),
        )
    }
}

/**
 * May trang thai phan biet fling xuat phat tu thao tac cham tay voi cuon non-touch
 * do ban phim hoac chuyen focus.
 *
 * Khac phuc bug vuot ngan tha tay (TYPE_NON_TOUCH fling): cho phep pre-scroll dy > 0
 * thu gon AppBar khi fling den tu cham tay, dong thoi giu chan cuon ban phim/focus.
 */
class XperiaAppBarScrollGate {
    private var touchActive = false
    private var pendingTouchFling = false
    private var touchFlingActive = false
    private var lastScrollDy = 0

    val isTouchFlingActive: Boolean
        get() = touchFlingActive

    val lastDirectionDy: Int
        get() = lastScrollDy

    fun onStartNestedScroll(isTouch: Boolean, acceptedBySuper: Boolean) {
        if (!acceptedBySuper) return
        if (isTouch) {
            touchActive = true
            pendingTouchFling = false
            touchFlingActive = false
            lastScrollDy = 0
        } else {
            touchFlingActive = touchActive || pendingTouchFling
            pendingTouchFling = false
        }
    }

    fun onNestedFlingDispatched() {
        if (touchActive) {
            pendingTouchFling = true
        }
    }

    fun recordPreScroll(dy: Int) {
        if (dy != 0) {
            lastScrollDy = dy
        }
    }

    fun recordPostScroll(dyUnconsumed: Int) {
        if (dyUnconsumed != 0) {
            lastScrollDy = dyUnconsumed
        }
    }

    /**
     * Tra ve true khi cu chi cuon thuc su ket thuc va duoc phep settle AppBar.
     * - stop(TOUCH) khi dang cho/chay fling: khong settle som de tranh nay giat.
     * - stop(NON_TOUCH) chi settle neu truoc do la fling tu cham tay.
     */
    fun consumeSettleOnStop(isTouch: Boolean): Boolean {
        if (isTouch) {
            touchActive = false
            return !pendingTouchFling && !touchFlingActive
        }
        val shouldSettle = touchFlingActive
        touchFlingActive = false
        pendingTouchFling = false
        return shouldSettle
    }

    fun onStopNestedScroll(isTouch: Boolean) {
        consumeSettleOnStop(isTouch)
    }

    fun shouldAllowNestedPreScroll(isTouch: Boolean, dy: Int): Boolean =
        isTouch || (touchFlingActive && dy > 0)

    fun shouldAllowNestedScroll(isTouch: Boolean, dyUnconsumed: Int): Boolean =
        isTouch || (touchFlingActive && dyUnconsumed < 0)

    fun resetDirection() {
        lastScrollDy = 0
    }

    fun reset() {
        touchActive = false
        pendingTouchFling = false
        touchFlingActive = false
        lastScrollDy = 0
    }
}
