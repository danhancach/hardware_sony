/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import kotlin.math.abs

/**
 * May trang thai phan xu truc vuot theo tung con tro (khong phu thuoc Android SDK).
 *
 * Tham chieu PagerAxisOwnership + LSPosed kc2AngleCommit:
 * - Trong nguong touchSlop: giu Undecided.
 * - Khi bang nhau (|dy| == |dx|): uu tien truc doc de bao ve thao tac cuon danh sach.
 * - Khoa Vertical: chiu troi ngang nhe (|dx| < 1.5 * |dy|) ma khong nhay nham widget ngang.
 * - Horizontal Takeover (|dx| > slop va |dx| >= 1.5 * |dy|): cho phep cu chi ngang ro ret
 *   tiep quan ngay ca khi ban dau nhich doc nhe.
 * - POINTER_UP: chuyen quyen sang pointer ke tiep va rebase diem goc, giu nguyen axis/claim.
 */
object XperiaTouchAxisOwnership {
    enum class Axis {
        Undecided,
        Vertical,
        Horizontal,
    }

    data class Config(
        val touchSlopPx: Float = 16f,
        val dominantRatio: Float = 1.0f,
        val horizontalTakeoverRatio: Float = 1.5f,
    )

    data class State(
        val pointerId: Int = -1,
        val downX: Float = 0f,
        val downY: Float = 0f,
        val axis: Axis = Axis.Undecided,
        val active: Boolean = false,
        val childHorizontalClaim: Boolean = false,
        val hasHorizontalTarget: Boolean = false,
    )

    fun down(
        state: State,
        pointerId: Int,
        x: Float,
        y: Float,
        childHorizontalClaim: Boolean = false,
        hasHorizontalTarget: Boolean = false,
    ): State =
        State(
            pointerId = pointerId,
            downX = x,
            downY = y,
            axis = Axis.Undecided,
            active = true,
            childHorizontalClaim = childHorizontalClaim,
            hasHorizontalTarget = hasHorizontalTarget,
        )

    fun move(
        state: State,
        pointerId: Int,
        x: Float,
        y: Float,
        config: Config = Config(),
    ): State {
        if (!state.active || pointerId != state.pointerId) return state
        if (state.axis == Axis.Horizontal) return state

        val dx = x - state.downX
        val dy = y - state.downY
        val adx = abs(dx)
        val ady = abs(dy)
        val slop = config.touchSlopPx.coerceAtLeast(0f)
        if (adx <= slop && ady <= slop) return state

        val ratio = config.dominantRatio.coerceAtLeast(0.01f)
        val takeover = config.horizontalTakeoverRatio.coerceAtLeast(0.01f)
        val axis = when {
            adx > slop && adx >= ady * takeover -> Axis.Horizontal
            state.axis == Axis.Vertical -> Axis.Vertical
            ady >= adx * ratio && ady > slop -> Axis.Vertical
            adx >= ady * ratio && adx > slop -> Axis.Horizontal
            else -> Axis.Undecided
        }
        return state.copy(axis = axis)
    }

    fun pointerEnded(
        state: State,
        endedPointerId: Int,
        successorId: Int? = null,
        successorX: Float = 0f,
        successorY: Float = 0f,
    ): State {
        if (!state.active) return state
        if (endedPointerId != state.pointerId) return state
        if (successorId == null) return reset()
        return state.copy(
            pointerId = successorId,
            downX = successorX,
            downY = successorY,
        )
    }

    fun up(state: State, pointerId: Int): State {
        if (!state.active) return state
        return if (pointerId == state.pointerId) reset() else state
    }

    fun cancel(state: State): State = reset()

    fun reset(): State = State()

    /** Chan View cha doc (RecyclerView/NestedScrollView) cuop cham khi con ngang dang so huu. */
    fun shouldBlockVerticalParent(state: State): Boolean =
        state.active && (state.childHorizontalClaim || (state.hasHorizontalTarget && state.axis == Axis.Horizontal))

    /** Giu disallow tam thoi khi DOWN tren widget ngang long doc de cho danh gia slop. */
    fun shouldRequestDisallowOnNestedHorizontalDown(state: State): Boolean =
        state.active && (state.childHorizontalClaim || state.hasHorizontalTarget)

    /** Nha disallow cho cha doc ngay khi vuot ro huong doc tren ViewPager2 / chip bar. */
    fun shouldReleaseNestedHorizontalParent(state: State): Boolean =
        state.active && !state.childHorizontalClaim && state.axis == Axis.Vertical

    /** Chi triet tieu do dan doc khi slider claim hoac khi vuot ngang thuc su tiep quan. */
    fun shouldClearVerticalTension(prev: State, next: State): Boolean {
        if (next.childHorizontalClaim && !prev.childHorizontalClaim) return true
        return next.active &&
            next.axis == Axis.Horizontal &&
            prev.axis != Axis.Horizontal &&
            (next.hasHorizontalTarget || next.childHorizontalClaim)
    }

    /**
     * Chi ngat fling chu dong o ACTION_DOWN khi danh sach dang co tension dan hoi
     * hoac nguoi dung cham vao widget ngang; tap item binh thuong tren list khong bi anh huong.
     */
    fun shouldStopListFlingOnDown(
        hasTension: Boolean,
        touchedHorizontalWidget: Boolean,
    ): Boolean = hasTension || touchedHorizontalWidget

    sealed class Event {
        data class Down(
            val id: Int,
            val x: Float,
            val y: Float,
            val childHorizontalClaim: Boolean = false,
            val hasHorizontalTarget: Boolean = false,
        ) : Event()

        data class Move(val id: Int, val x: Float, val y: Float) : Event()
        data class Up(val id: Int) : Event()
        data class PointerUp(
            val id: Int,
            val successorId: Int? = null,
            val successorX: Float = 0f,
            val successorY: Float = 0f,
        ) : Event()

        data object Cancel : Event()
    }

    fun reduce(events: List<Event>, config: Config = Config()): State {
        var s = reset()
        for (e in events) {
            s = when (e) {
                is Event.Down -> down(
                    state = s,
                    pointerId = e.id,
                    x = e.x,
                    y = e.y,
                    childHorizontalClaim = e.childHorizontalClaim,
                    hasHorizontalTarget = e.hasHorizontalTarget,
                )
                is Event.Move -> move(s, e.id, e.x, e.y, config)
                is Event.Up -> up(s, e.id)
                is Event.PointerUp -> pointerEnded(
                    state = s,
                    endedPointerId = e.id,
                    successorId = e.successorId,
                    successorX = e.successorX,
                    successorY = e.successorY,
                )
                Event.Cancel -> cancel(s)
            }
        }
        return s
    }
}
