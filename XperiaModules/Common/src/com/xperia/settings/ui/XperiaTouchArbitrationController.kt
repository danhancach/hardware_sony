/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/**
 * Bo dieu phoi phan xu cu chi vuot ngang/doc o tang Activity cho toan bo Xperia Settings.
 *
 * - Hit-test theo toa do man hinh (getLocationOnScreen + rawX/rawY) de tranh sai lech do cuon/translation.
 * - Bao ve Slider/SeekBar va widget cuon ngang (ViewPager2, HorizontalScrollView) khoi bi list doc cuop cham.
 * - Khi cu chi ngang thuc su tiep quan, ngat fling doc va xoa do dan overscroll tuc thi.
 */
class XperiaTouchArbitrationController(
    context: Context,
    private val rootProvider: () -> View?,
) {
    private val config = XperiaTouchAxisOwnership.Config(
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
    )
    private var ownership = XperiaTouchAxisOwnership.reset()
    private var verticalHost: ViewGroup? = null

    fun dispatch(ev: MotionEvent, superDispatch: (MotionEvent) -> Boolean): Boolean {
        val root = rootProvider()
        val action = ev.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val deepest = root?.let { findDeepestViewAtRaw(it, ev.rawX, ev.rawY) }
                val sliderClaim = deepest != null && hasAncestorMatching(deepest, root, ::isSliderOrSeekBar)
                val horizontalTarget = deepest != null &&
                    hasAncestorMatching(deepest, root, ::isHorizontalScrollTarget)
                verticalHost = if (deepest != null) {
                    findNearestVerticalScrollHost(deepest, root)
                } else {
                    null
                }
                XperiaMiuixScrollCoordinator.reacquireFromTouch(
                    scopeRoot = root,
                    touchedHorizontalWidget = sliderClaim || horizontalTarget,
                    downTime = ev.downTime,
                )
                ownership = XperiaTouchAxisOwnership.down(
                    state = ownership,
                    pointerId = ev.getPointerId(0),
                    x = ev.rawX,
                    y = ev.rawY,
                    childHorizontalClaim = sliderClaim,
                    hasHorizontalTarget = horizontalTarget,
                )
                if (sliderClaim) {
                    XperiaMiuixScrollCoordinator.confirmHorizontalTakeoverClearTension(root)
                    verticalHost?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (ownership.active) {
                    val idx = ev.findPointerIndex(ownership.pointerId)
                    if (idx >= 0) {
                        val prev = ownership
                        val next = XperiaTouchAxisOwnership.move(
                            state = ownership,
                            pointerId = ownership.pointerId,
                            x = XperiaTouchArbitrationController.rawXForIndex(ev, idx),
                            y = XperiaTouchArbitrationController.rawYForIndex(ev, idx),
                            config = config,
                        )
                        if (XperiaTouchAxisOwnership.shouldClearVerticalTension(prev, next)) {
                            XperiaMiuixScrollCoordinator.confirmHorizontalTakeoverClearTension(root)
                        }
                        ownership = next
                        if (XperiaTouchAxisOwnership.shouldBlockVerticalParent(ownership)) {
                            verticalHost?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val endedId = ev.getPointerId(ev.actionIndex)
                val succ = successorRawPointer(ev, endedId)
                ownership = XperiaTouchAxisOwnership.pointerEnded(
                    state = ownership,
                    endedPointerId = endedId,
                    successorId = succ?.first,
                    successorX = succ?.second ?: 0f,
                    successorY = succ?.third ?: 0f,
                )
            }
        }

        val handled = superDispatch(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // ViewGroup.dispatchTouchEvent(DOWN) xoa FLAG_DISALLOW_INTERCEPT -> bat buoc khoi phuc sau dispatch
                if (XperiaTouchAxisOwnership.shouldBlockVerticalParent(ownership)) {
                    verticalHost?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP -> {
                verticalHost?.requestDisallowInterceptTouchEvent(false)
                verticalHost = null
                ownership = XperiaTouchAxisOwnership.up(ownership, ev.getPointerId(ev.actionIndex))
                XperiaMiuixScrollCoordinator.onTouchUpOrCancel(root)
            }
            MotionEvent.ACTION_CANCEL -> {
                verticalHost?.requestDisallowInterceptTouchEvent(false)
                verticalHost = null
                ownership = XperiaTouchAxisOwnership.cancel(ownership)
                XperiaMiuixScrollCoordinator.onTouchUpOrCancel(root)
            }
        }

        return handled
    }

    companion object {
        private const val VIEWPAGER2_CLASS_NAME = "androidx.viewpager2.widget.ViewPager2"
        private const val MATERIAL_SLIDER = "com.google.android.material.slider.Slider"
        private const val MATERIAL_BASE_SLIDER = "com.google.android.material.slider.BaseSlider"
        private const val MATERIAL_RANGE_SLIDER = "com.google.android.material.slider.RangeSlider"
        private val hitLocation = IntArray(2)

        internal fun rawXForIndex(ev: MotionEvent, index: Int): Float =
            if (index == 0) ev.rawX else ev.rawX + (ev.getX(index) - ev.getX(0))

        internal fun rawYForIndex(ev: MotionEvent, index: Int): Float =
            if (index == 0) ev.rawY else ev.rawY + (ev.getY(index) - ev.getY(0))

        internal fun successorRawPointer(ev: MotionEvent, endedId: Int): Triple<Int, Float, Float>? {
            for (i in 0 until ev.pointerCount) {
                val id = ev.getPointerId(i)
                if (id != endedId) {
                    return Triple(id, rawXForIndex(ev, i), rawYForIndex(ev, i))
                }
            }
            return null
        }

        fun findDeepestViewAtRaw(root: View, rawX: Float, rawY: Float): View? {
            if (root.visibility != View.VISIBLE || !isRawPointInView(root, rawX, rawY)) {
                return null
            }
            if (root is ViewGroup) {
                for (i in root.childCount - 1 downTo 0) {
                    val deeper = findDeepestViewAtRaw(root.getChildAt(i), rawX, rawY)
                    if (deeper != null) return deeper
                }
            }
            return root
        }

        private fun isRawPointInView(view: View, rawX: Float, rawY: Float): Boolean {
            if (view.width <= 0 || view.height <= 0) return false
            view.getLocationOnScreen(hitLocation)
            val left = hitLocation[0].toFloat()
            val top = hitLocation[1].toFloat()
            return rawX >= left && rawY >= top &&
                rawX < left + view.width && rawY < top + view.height
        }

        fun hasAncestorMatching(start: View, stopAt: View?, predicate: (View) -> Boolean): Boolean {
            var cur: View? = start
            while (cur != null) {
                if (predicate(cur)) return true
                if (cur === stopAt) break
                cur = cur.parent as? View
            }
            return false
        }

        fun findNearestVerticalScrollHost(start: View, stopAt: View?): ViewGroup? {
            var cur: View? = start
            while (cur != null) {
                if (cur is NestedScrollView) return cur
                if (cur is RecyclerView &&
                    cur.layoutManager?.canScrollHorizontally() != true &&
                    !isViewPager2Class(cur.parent)
                ) {
                    return cur
                }
                if (cur === stopAt) break
                cur = cur.parent as? View
            }
            return null
        }

        fun isSliderOrSeekBar(view: View): Boolean {
            if (view is SeekBar) return true
            var cur: Class<*>? = view.javaClass
            while (cur != null) {
                when (cur.name) {
                    MATERIAL_SLIDER, MATERIAL_BASE_SLIDER, MATERIAL_RANGE_SLIDER -> return true
                }
                cur = cur.superclass
            }
            return false
        }

        fun isHorizontalScrollTarget(view: View): Boolean {
            if (view is HorizontalScrollView) return true
            if (view is RecyclerView && view.layoutManager?.canScrollHorizontally() == true) {
                return true
            }
            return isViewPager2Class(view)
        }

        internal fun isViewPager2Class(obj: Any?): Boolean {
            var cur: Class<*>? = obj?.javaClass
            while (cur != null) {
                if (cur.name == VIEWPAGER2_CLASS_NAME) return true
                cur = cur.superclass
            }
            return false
        }
    }
}

/**
 * Wrapper XML bao ve widget cuon ngang/Slider long trong danh sach doc hoac BottomSheet.
 *
 * - ACTION_DOWN: giu disallow tam thoi tren cha doc (ca truoc va sau super) de cho danh gia slop.
 * - ACTION_MOVE: vuot doc -> nha ngay cho cha cuon; vuot ngang -> khoa cha va xoa tension doc.
 */
class XperiaNestedHorizontalAxisGuard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val config = XperiaTouchAxisOwnership.Config(
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
    )
    private var ownership = XperiaTouchAxisOwnership.reset()
    private var horizontalTakeoverReported = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                horizontalTakeoverReported = false
                val deepest = XperiaTouchArbitrationController.findDeepestViewAtRaw(
                    this,
                    ev.rawX,
                    ev.rawY,
                )
                val sliderClaim = deepest != null &&
                    XperiaTouchArbitrationController.hasAncestorMatching(
                        deepest,
                        this,
                        XperiaTouchArbitrationController::isSliderOrSeekBar,
                    )
                ownership = XperiaTouchAxisOwnership.down(
                    state = ownership,
                    pointerId = ev.getPointerId(0),
                    x = ev.rawX,
                    y = ev.rawY,
                    childHorizontalClaim = sliderClaim,
                    hasHorizontalTarget = true,
                )
                if (XperiaTouchAxisOwnership.shouldRequestDisallowOnNestedHorizontalDown(ownership)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (ownership.active) {
                    val idx = ev.findPointerIndex(ownership.pointerId)
                    if (idx >= 0) {
                        val prev = ownership
                        val next = XperiaTouchAxisOwnership.move(
                            state = ownership,
                            pointerId = ownership.pointerId,
                            x = XperiaTouchArbitrationController.rawXForIndex(ev, idx),
                            y = XperiaTouchArbitrationController.rawYForIndex(ev, idx),
                            config = config,
                        )
                        if (!horizontalTakeoverReported &&
                            XperiaTouchAxisOwnership.shouldClearVerticalTension(prev, next)
                        ) {
                            horizontalTakeoverReported = true
                            XperiaMiuixScrollCoordinator.confirmHorizontalTakeoverClearTension(rootView)
                        }
                        ownership = next
                        when {
                            XperiaTouchAxisOwnership.shouldReleaseNestedHorizontalParent(ownership) ->
                                parent?.requestDisallowInterceptTouchEvent(false)
                            XperiaTouchAxisOwnership.shouldBlockVerticalParent(ownership) ->
                                parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val endedId = ev.getPointerId(ev.actionIndex)
                val succ = XperiaTouchArbitrationController.successorRawPointer(ev, endedId)
                ownership = XperiaTouchAxisOwnership.pointerEnded(
                    state = ownership,
                    endedPointerId = endedId,
                    successorId = succ?.first,
                    successorX = succ?.second ?: 0f,
                    successorY = succ?.third ?: 0f,
                )
            }
        }

        val handled = super.dispatchTouchEvent(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // Khoi phuc disallow sau khi ViewGroup cha/con xu ly DOWN
                if (XperiaTouchAxisOwnership.shouldRequestDisallowOnNestedHorizontalDown(ownership)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                horizontalTakeoverReported = false
                ownership = XperiaTouchAxisOwnership.up(ownership, ev.getPointerId(ev.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                horizontalTakeoverReported = false
                ownership = XperiaTouchAxisOwnership.cancel(ownership)
            }
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        parent?.requestDisallowInterceptTouchEvent(false)
        horizontalTakeoverReported = false
        ownership = XperiaTouchAxisOwnership.reset()
        super.onDetachedFromWindow()
    }
}
