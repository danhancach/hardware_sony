/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import kotlin.math.abs
import kotlin.math.sign

/**
 * Phan xu pha cuon long nhau Miuix cho Xperia Settings (khong phu thuoc Android SDK).
 *
 * Thu tu pha nghiem ngat theo .docs/ui/lsposed-miuix-rea.md muc 3E:
 * - PreScroll:  1) giai toa tension nguoc chieu (R0) -> 2) thu gon tieu de (header collapse)
 * - Child:      cuon danh sach noi dung
 * - PostScroll: 3) mo rong tieu de khi cham dinh (header expand) -> 4) keo dan mep hoac trang ngan
 * - Fling:      F0/I0 phan bo van toc giua stretch va child list
 *
 * Quy uoc dau Miuix: availableY < 0 = vuot len (thu gon header / cuon xuong cuoi);
 * availableY > 0 = vuot xuong (cuon ve dinh / mo rong header).
 */
object XperiaScrollPhase {
    const val EpsilonPx = XperiaStretchMath.VisualEpsilonPx

    const val PostFlingVelocityDivisor = 1.53333f
    const val PreFlingOppositeConsumeDivisor = 2.13333f

    data class Caps(
        val headerCollapseRemaining: Float,
        val headerExpandRemaining: Float,
        val contentUpRemaining: Float,
        val contentDownRemaining: Float,
        val tensionE: Float = 0f,
        val boundG: Float = 0f,
        val tensionRawF: Float = 0f,
        val tensionUnbound: Boolean = false,
    ) {
        val canContentScroll: Boolean
            get() = contentUpRemaining > EpsilonPx || contentDownRemaining > EpsilonPx

        val headerFullyExpanded: Boolean
            get() = headerExpandRemaining <= EpsilonPx

        val canTopStretch: Boolean
            get() = headerFullyExpanded && contentUpRemaining <= EpsilonPx

        val canBottomStretch: Boolean
            get() = contentDownRemaining <= EpsilonPx
    }

    data class PreResult(
        val tensionConsumed: Float,
        val headerConsumed: Float,
        val toChild: Float,
        val nextTensionState: XperiaStretchMath.TensionState,
        val nextCollapseRemaining: Float,
    )

    data class PostResult(
        val headerConsumed: Float,
        val stretchConsumed: Float,
        val nextExpandRemaining: Float,
        val nextTensionState: XperiaStretchMath.TensionState,
    )

    private fun initialTensionState(caps: Caps): XperiaStretchMath.TensionState {
        if (caps.boundG <= EpsilonPx) {
            return XperiaStretchMath.TensionState()
        }
        if (caps.tensionRawF != 0f) {
            // Giu rawF goc de tich luy subpixel khi keo cham 1px/frame khong bi troi do pow(1/3)
            return XperiaStretchMath.TensionState(
                rawF = caps.tensionRawF,
                visualE = caps.tensionE,
                unbound = caps.tensionUnbound,
            )
        }
        return XperiaStretchMath.reacquireAfterInterrupt(caps.tensionE, caps.boundG)
    }

    /** Pha PreScroll: chi giai tension nguoc chieu va thu gon header; khong tao stretch moi. */
    fun preScroll(availableY: Float, caps: Caps): PreResult {
        var remaining = availableY
        var tensionClaim = 0f
        var tensionState = initialTensionState(caps)
        var collapseLeft = caps.headerCollapseRemaining.coerceAtLeast(0f)

        if (remaining != 0f &&
            caps.boundG > EpsilonPx &&
            (abs(tensionState.visualE) > EpsilonPx || tensionState.rawF != 0f)
        ) {
            val (next, consumed) = XperiaStretchMath.reverseRelease(
                tensionState,
                remaining,
                caps.boundG,
            )
            if (consumed != 0f) {
                tensionClaim = consumed
                remaining -= consumed
                tensionState = next
            }
        }

        if (remaining == 0f) {
            return PreResult(
                tensionConsumed = tensionClaim,
                headerConsumed = 0f,
                toChild = 0f,
                nextTensionState = tensionState,
                nextCollapseRemaining = collapseLeft,
            )
        }

        var headerClaim = 0f
        if (remaining < 0f && collapseLeft > EpsilonPx) {
            val take = maxOf(remaining, -collapseLeft)
            headerClaim = take
            collapseLeft += take
            remaining -= take
        }

        return PreResult(
            tensionConsumed = tensionClaim,
            headerConsumed = headerClaim,
            toChild = remaining,
            nextTensionState = tensionState,
            nextCollapseRemaining = collapseLeft.coerceAtLeast(0f),
        )
    }

    /** Pha PostScroll: mo rong header sau khi list cham dinh, roi moi dan hoi o mep/trang ngan. */
    fun postScroll(availableY: Float, caps: Caps, allowStretch: Boolean = true): PostResult {
        var remaining = availableY
        var expandLeft = caps.headerExpandRemaining.coerceAtLeast(0f)
        var headerClaim = 0f
        var stretchClaim = 0f
        var tensionState = initialTensionState(caps)

        if (remaining > 0f && expandLeft > EpsilonPx && caps.contentUpRemaining <= EpsilonPx) {
            val take = minOf(remaining, expandLeft)
            headerClaim = take
            expandLeft -= take
            remaining -= take
        }

        if (allowStretch && remaining != 0f && caps.boundG > EpsilonPx) {
            val wantTop = remaining > 0f && caps.canTopStretch
            val wantBottom = remaining < 0f && caps.canBottomStretch
            val shortAll = !caps.canContentScroll
            if (wantTop || wantBottom || shortAll) {
                val start = tensionState
                val next = XperiaStretchMath.applyDelta(start, remaining, caps.boundG)
                val actual = if (next.unbound || start.unbound) {
                    next.visualE - start.visualE
                } else {
                    next.rawF - start.rawF
                }
                stretchClaim = actual
                tensionState = next
            }
        }

        return PostResult(
            headerConsumed = headerClaim,
            stretchConsumed = stretchClaim,
            nextExpandRemaining = expandLeft.coerceAtLeast(0f),
            nextTensionState = tensionState,
        )
    }

    fun flingPreConsumedY(visualE: Float, flingVy: Float): Float {
        if (abs(visualE) <= EpsilonPx || flingVy == 0f) return 0f
        return if (sign(flingVy) == sign(visualE)) {
            flingVy
        } else {
            flingVy / PreFlingOppositeConsumeDivisor
        }
    }

    fun flingSettleVelocity(visualE: Float, leftoverY: Float): Float? = when {
        leftoverY != 0f -> leftoverY / PostFlingVelocityDivisor
        abs(visualE) > EpsilonPx -> 0f
        else -> null
    }

    fun capsFromViewMetrics(
        appBarTop: Int,
        totalScrollRange: Int,
        contentUpRemaining: Float,
        contentDownRemaining: Float,
        tensionE: Float,
        boundG: Float,
        tensionRawF: Float = 0f,
        tensionUnbound: Boolean = false,
    ): Caps {
        val range = totalScrollRange.coerceAtLeast(0).toFloat()
        val collapsedAmount = (-appBarTop).toFloat().coerceIn(0f, range)
        return Caps(
            headerCollapseRemaining = (range - collapsedAmount).coerceAtLeast(0f),
            headerExpandRemaining = collapsedAmount,
            contentUpRemaining = contentUpRemaining.coerceAtLeast(0f),
            contentDownRemaining = contentDownRemaining.coerceAtLeast(0f),
            tensionE = tensionE,
            boundG = boundG,
            tensionRawF = tensionRawF,
            tensionUnbound = tensionUnbound,
        )
    }
}
