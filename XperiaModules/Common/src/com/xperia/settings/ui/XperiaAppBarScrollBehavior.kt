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

/**
 * AppBarLayout.Behavior sua loi vuot ngan tha tay khong thu gon tieu de.
 *
 * - Cho phep fling tu cham tay tiep tuc thu gon AppBar o onNestedPreScroll (dy > 0).
 * - Cho phep fling cham dinh danh sach mo rong AppBar o onNestedScroll (dyUnconsumed < 0).
 * - Van chan non-touch do ban phim / focus de tranh nhap nhay tieu de.
 */
class XperiaAppBarScrollBehavior : AppBarLayout.Behavior {

    private val allowHeaderDrag: Boolean
    private val gate = XperiaAppBarScrollGate()

    @JvmOverloads
    constructor(allowHeaderDrag: Boolean = false) : super() {
        this.allowHeaderDrag = allowHeaderDrag
        installDragCallback()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.allowHeaderDrag = false
        installDragCallback()
    }

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
        if (!gate.shouldAllowNestedPreScroll(type == ViewCompat.TYPE_TOUCH, dy)) {
            return
        }
        gate.recordPreScroll(dy)
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
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
        if (gate.shouldAllowNestedScroll(type == ViewCompat.TYPE_TOUCH, dyUnconsumed)) {
            gate.recordPostScroll(dyUnconsumed)
            super.onNestedScroll(
                coordinatorLayout,
                child,
                target,
                dxConsumed,
                dyConsumed,
                dxUnconsumed,
                dyUnconsumed,
                type,
                consumed,
            )
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        type: Int,
    ) {
        val shouldSettle = gate.consumeSettleOnStop(type == ViewCompat.TYPE_TOUCH)
        super.onStopNestedScroll(coordinatorLayout, child, target, type)
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
