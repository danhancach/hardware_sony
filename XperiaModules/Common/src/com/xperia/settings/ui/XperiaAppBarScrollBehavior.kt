/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.roundToInt

/**
 * Cau noi giua XperiaAppBarScrollBehavior va bo dieu khien dan hoi cuon.
 */
interface XperiaPhaseScrollOwner {
    fun liveCaps(): XperiaScrollPhase.Caps
    fun commitTension(state: XperiaStretchMath.TensionState)
    fun onNestedPreFling(velocityX: Float, velocityY: Float): Boolean = false
    fun onNestedFling(velocityY: Float, consumed: Boolean) {}
    fun onNestedTouchScrollStopped() {}
}

/**
 * AppBarLayout.Behavior hop nhat 4 pha cuon Miuix va fling gate tu cham tay.
 *
 * - PreScroll: giai tension nguoc chieu truoc -> thu gon AppBar -> chuyen cho danh sach.
 * - PostScroll: mo rong AppBar khi cham dinh -> keo dan mep hoac trang ngan.
 * - Cho phep fling tu cham tay tiep tuc thu gon/mo rong AppBar va giu chan non-touch ban phim.
 */
class XperiaAppBarScrollBehavior : AppBarLayout.Behavior {

    private val allowHeaderDrag: Boolean
    private val gate = XperiaAppBarScrollGate()

    @Volatile
    private var phaseOwner: XperiaPhaseScrollOwner? = null

    @JvmOverloads
    constructor(allowHeaderDrag: Boolean = false) : super() {
        this.allowHeaderDrag = allowHeaderDrag
        installDragCallback()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.allowHeaderDrag = false
        installDragCallback()
    }

    fun attachPhaseOwner(owner: XperiaPhaseScrollOwner?) {
        phaseOwner = owner
    }

    fun phaseOwnerOrNull(): XperiaPhaseScrollOwner? = phaseOwner

    private fun installDragCallback() {
        setDragCallback(object : DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean = allowHeaderDrag
        })
    }

    override fun onStartNestedScroll(
        parent: CoordinatorLayout,
        child: AppBarLayout,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int,
        type: Int,
    ): Boolean {
        val accepted = super.onStartNestedScroll(
            parent,
            child,
            directTargetChild,
            target,
            nestedScrollAxes,
            type,
        )
        gate.onStartNestedScroll(type == ViewCompat.TYPE_TOUCH, accepted)
        return accepted
    }

    override fun onNestedPreFling(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        gate.onNestedFlingDispatched()
        if (phaseOwner?.onNestedPreFling(velocityX, velocityY) == true) {
            return true
        }
        return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY)
    }

    override fun onNestedFling(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean {
        gate.onNestedFlingDispatched()
        phaseOwner?.onNestedFling(velocityY, consumed)
        return super.onNestedFling(coordinatorLayout, child, target, velocityX, velocityY, consumed)
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        val isTouch = type == ViewCompat.TYPE_TOUCH
        if (!gate.shouldAllowNestedPreScroll(isTouch, dy)) {
            return
        }
        gate.recordPreScroll(dy)
        val owner = phaseOwner
        if (owner == null || dy == 0) {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
            return
        }

        val caps = owner.liveCaps().let {
            if (isTouch) it else it.copy(tensionE = 0f, tensionRawF = 0f, tensionUnbound = false)
        }
        val pre = XperiaScrollPhase.preScroll(-dy.toFloat(), caps)
        val tensionDy = (-pre.tensionConsumed).roundToInt()
            .coerceIn(minOf(0, dy), maxOf(0, dy))
        if (tensionDy != 0) {
            owner.commitTension(pre.nextTensionState)
        }

        val afterTension = dy - tensionDy
        val headerConsumed = IntArray(2)
        if (afterTension != 0) {
            super.onNestedPreScroll(
                coordinatorLayout,
                child,
                target,
                dx,
                afterTension,
                headerConsumed,
                type,
            )
        }
        consumed[0] += headerConsumed[0]
        consumed[1] += tensionDy + headerConsumed[1]
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        val isTouch = type == ViewCompat.TYPE_TOUCH
        if (!gate.shouldAllowNestedScroll(isTouch, dyUnconsumed)) {
            return
        }
        gate.recordPostScroll(dyUnconsumed)

        val headerConsumed = IntArray(2)
        super.onNestedScroll(
            coordinatorLayout,
            child,
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type,
            headerConsumed,
        )
        consumed[0] += headerConsumed[0]
        consumed[1] += headerConsumed[1]

        val owner = phaseOwner ?: return
        val remainingDy = dyUnconsumed - headerConsumed[1]
        if (remainingDy == 0 || !isTouch) return

        val post = XperiaScrollPhase.postScroll(-remainingDy.toFloat(), owner.liveCaps())
        if (post.headerConsumed != 0f || post.stretchConsumed == 0f) return
        val stretchDy = (-post.stretchConsumed).roundToInt()
            .coerceIn(minOf(0, remainingDy), maxOf(0, remainingDy))
        if (stretchDy != 0) {
            owner.commitTension(post.nextTensionState)
            consumed[1] += stretchDy
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        type: Int,
    ) {
        val isTouch = type == ViewCompat.TYPE_TOUCH
        val shouldSettle = gate.consumeSettleOnStop(isTouch)
        super.onStopNestedScroll(coordinatorLayout, child, target, type)
        if (isTouch) {
            phaseOwner?.onNestedTouchScrollStopped()
        }
        if (!shouldSettle) {
            return
        }
        val range = child.totalScrollRange
        if (range > 0) {
            val fraction = XperiaMiuixTitleMotion.collapseFraction(
                topAndBottomOffset.toFloat(),
                range.toFloat(),
            )
            if (fraction > 0f && fraction < 1f) {
                val snapCollapsed = XperiaMiuixTitleMotion.shouldSnapToCollapsed(
                    collapseFraction = fraction,
                    lastScrollDy = gate.lastDirectionDy,
                    canContentScrollUp = target.canScrollVertically(-1),
                )
                child.setExpanded(!snapCollapsed, true)
            }
        }
        gate.resetDirection()
    }
}
