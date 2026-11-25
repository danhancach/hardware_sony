/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.withSign

/**
 * Toan hoc dan hoi Miuix Cubic Stretch va od3 settle (khong phu thuoc Android SDK).
 *
 * Quy chuan theo .docs/ui/lsposed-miuix-rea.md muc 3F-3G:
 * - g(t) = t - t^2 + t^3 / 3 voi t = |F| / G in [0, 1], do dan toi da E_max = G / 3.
 * - Giai nguoc cubic kh2.y de reacquire muot khi ngat animation hoac doi chieu keo.
 * - Tich phan hoi phuc od3 khi buong tay: |v| > 5000px/s -> 0.55s, nguoc lai 0.40s.
 */
object XperiaStretchMath {
    const val VisualEpsilonPx = 1.0f

    const val SettlePeriodFastVelocity = 5000.0
    const val SettlePeriodHighVelocity = 0.55
    const val SettlePeriodNormal = 0.4

    data class TensionState(
        val rawF: Float = 0f,
        val visualE: Float = 0f,
        val unbound: Boolean = false,
    )

    fun stretchVisual(rawF: Float, boundG: Float): Float {
        if (rawF == 0f || boundG <= 0f) return 0f
        val t = (abs(rawF) / boundG).coerceIn(0f, 1f).toDouble()
        val g = t - t.pow(2.0) + t.pow(3.0) / 3.0
        return (sign(rawF) * g * boundG).toFloat()
    }

    fun inverseStretch(visualE: Float, boundG: Float): Float {
        if (visualE == 0f || boundG <= 0f) return 0f
        val maxVisual = abs(stretchVisual(boundG, boundG))
        val clamped = abs(visualE).coerceAtMost(maxVisual).toDouble()
        val d3 = boundG.toDouble() - clamped * 3.0
        val rawAbs =
            boundG.toDouble() -
                abs(d3).pow(1.0 / 3.0) * sign(d3) * boundG.toDouble().pow(2.0 / 3.0)
        return rawAbs.toFloat().withSign(visualE)
    }

    fun maxDragVisual(boundG: Float): Float = abs(stretchVisual(boundG, boundG))

    /** Khoi phuc trang thai keo tu vi tri hien tai sau khi ngat animation settle (B1). */
    fun reacquireAfterInterrupt(visualE: Float, boundG: Float): TensionState {
        if (abs(visualE) <= VisualEpsilonPx || boundG <= 0f) {
            return TensionState()
        }
        val maxE = maxDragVisual(boundG)
        return if (abs(visualE) > maxE + VisualEpsilonPx) {
            TensionState(
                rawF = boundG * sign(visualE),
                visualE = visualE,
                unbound = true,
            )
        } else {
            TensionState(
                rawF = inverseStretch(visualE, boundG),
                visualE = visualE,
                unbound = false,
            )
        }
    }

    /** Nap delta keo moi vao trang thai dan hoi, giu tich luy subpixel khi rawF != 0. */
    fun applyDelta(state: TensionState, delta: Float, boundG: Float): TensionState {
        if (delta == 0f || boundG <= VisualEpsilonPx) return state
        if (state.unbound) {
            var e = state.visualE + delta
            val maxE = maxDragVisual(boundG)
            if (abs(e) <= VisualEpsilonPx) {
                return TensionState()
            }
            if (abs(e) <= maxE + VisualEpsilonPx) {
                val f = inverseStretch(e, boundG)
                e = stretchVisual(f, boundG)
                return TensionState(rawF = f, visualE = e, unbound = false)
            }
            return TensionState(
                rawF = boundG * sign(e),
                visualE = e,
                unbound = true,
            )
        }
        val f = (state.rawF + delta).coerceIn(-boundG, boundG)
        if (f == 0f) {
            return TensionState()
        }
        val e = stretchVisual(f, boundG)
        return TensionState(rawF = f, visualE = e, unbound = false)
    }

    /**
     * Giai toa luc cang khi cuon nguoc chieu tension hien tai.
     * Tra ve (trang thai moi, luong delta da dung cung dau voi deltaY).
     */
    fun reverseRelease(
        state: TensionState,
        deltaY: Float,
        boundG: Float,
    ): Pair<TensionState, Float> {
        if (deltaY == 0f || boundG <= VisualEpsilonPx) return state to 0f
        if (state.rawF == 0f && abs(state.visualE) <= VisualEpsilonPx) {
            return state to 0f
        }
        val tensionSign = when {
            abs(state.visualE) > VisualEpsilonPx -> sign(state.visualE)
            state.rawF != 0f -> sign(state.rawF)
            else -> 0f
        }
        if (tensionSign == 0f || sign(deltaY) == tensionSign) {
            return state to 0f
        }
        if (state.unbound) {
            val toZero = -state.visualE
            return if (abs(deltaY) <= abs(toZero) + VisualEpsilonPx) {
                applyDelta(state, deltaY, boundG) to deltaY
            } else {
                TensionState() to toZero
            }
        }
        if (state.rawF == 0f) return state to 0f
        if (sign(deltaY) == sign(state.rawF)) return state to 0f
        return if (abs(state.rawF) <= abs(deltaY)) {
            TensionState() to -state.rawF
        } else {
            applyDelta(state, deltaY, boundG) to deltaY
        }
    }

    fun settlePeriodSeconds(velocity: Float): Double =
        if (abs(velocity.toDouble()) > SettlePeriodFastVelocity) {
            SettlePeriodHighVelocity
        } else {
            SettlePeriodNormal
        }

    /** Mot buoc tich phan vi phan hoi phuc od3 ve 0. */
    fun settleStep(
        pos: Double,
        vel: Double,
        dt: Double,
        period: Double,
    ): Pair<Double, Double> {
        val omega = (Math.PI * 2.0) / period
        val dampA = 2.0 * omega
        val stiffB = omega * omega
        val target = 0.0
        val clampedDt = dt.coerceIn(0.001, 0.016)
        val nextVel = vel * (1.0 - dampA * clampedDt) + (target - pos) * stiffB * clampedDt
        val nextPos = pos + nextVel * clampedDt
        return nextPos to nextVel
    }

    /** Dung ngay khi vuot qua moc 0 de khong rung lac nay nguoc. */
    fun shouldClearSettle(pos: Double, vel: Double, prevPos: Double): Boolean {
        val target = 0.0
        val crossed =
            (prevPos > target && pos <= target) ||
                (prevPos < target && pos >= target)
        if (crossed) return true
        if (abs(pos - target) < VisualEpsilonPx && abs(vel) < VisualEpsilonPx) return true
        return false
    }

    fun settleUntilZero(
        startPos: Double,
        startVel: Double,
        period: Double,
        scale: Double = 1.0,
        maxSteps: Int = 600,
        dt: Double = 1.0 / 60.0,
    ): Pair<Double, Int> {
        if (scale <= 0.0) return 0.0 to 0
        var pos = startPos
        var vel = startVel
        var prev = pos
        val scaledDt = (dt / scale.coerceAtLeast(0.01)).coerceIn(0.001, 0.016)
        for (i in 1..maxSteps) {
            val step = settleStep(pos, vel, scaledDt, period)
            if (shouldClearSettle(step.first, step.second, prev)) {
                return 0.0 to i
            }
            prev = pos
            pos = step.first
            vel = step.second
        }
        return 0.0 to maxSteps
    }
}
