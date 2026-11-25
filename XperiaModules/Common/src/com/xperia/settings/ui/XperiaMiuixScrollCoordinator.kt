/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import android.content.Context
import android.graphics.Canvas
import android.provider.Settings
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Bo phoi hop cuon va dan hoi Miuix thong nhat cho RecyclerView doc va NestedScrollView
 * trong toan bo man hinh Xperia Settings.
 *
 * - Bo qua RecyclerView cuon ngang va RecyclerView noi bo cua ViewPager2.
 * - Dong bo 4 pha cuon (R0 -> thu gon header -> list -> dan hoi mep/trang ngan).
 * - Bat ACTION_DOWN ngay ca tren item bam duoc de ngat settle va reacquire khong tre.
 */
object XperiaMiuixScrollCoordinator {
    private val attached = WeakHashMap<View, Attachment>()

    private class Attachment(
        val controller: PhaseStretchController,
        val attachListener: View.OnAttachStateChangeListener,
        val rvTouchListener: RecyclerView.OnItemTouchListener?,
        val previousFlingListener: RecyclerView.OnFlingListener?,
        val originalOverScrollMode: Int,
    )

    /** Tu dong tim va gan cho tat ca scrollable doc hop le trong cay view. */
    fun attachInHierarchy(root: View, explicitAppBar: AppBarLayout? = null) {
        if (isEligibleScrollable(root)) {
            attach(root, explicitAppBar)
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                attachInHierarchy(root.getChildAt(i), explicitAppBar)
            }
        }
    }

    fun attach(view: View, explicitAppBar: AppBarLayout? = null) {
        if (attached.containsKey(view)) return
        if (!isEligibleScrollable(view)) return

        val controller = PhaseStretchController(view, explicitAppBar)
        val originalOverScrollMode = view.overScrollMode

        var rvTouchListener: RecyclerView.OnItemTouchListener? = null
        var previousFlingListener: RecyclerView.OnFlingListener? = null

        when (view) {
            is RecyclerView -> {
                view.overScrollMode = View.OVER_SCROLL_ALWAYS
                view.isNestedScrollingEnabled = true
                view.clipChildren = true
                view.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                    override fun createEdgeEffect(rv: RecyclerView, direction: Int): EdgeEffect {
                        val isTop = direction == DIRECTION_TOP
                        return PhaseGatedEdgeEffect(rv.context, controller, isTop)
                    }
                }
                val touchListener = object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> controller.reacquireFromTouch(false, event.downTime)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> controller.release()
                        }
                        return false
                    }
                }
                view.addOnItemTouchListener(touchListener)
                rvTouchListener = touchListener

                previousFlingListener = view.onFlingListener
                val prevListener = previousFlingListener
                view.onFlingListener = object : RecyclerView.OnFlingListener() {
                    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                        if (controller.forwardingFling) return false
                        if (prevListener?.onFling(velocityX, velocityY) == true) return true
                        return controller.handoffFling(velocityX, velocityY)
                    }
                }
            }
            is NestedScrollView -> {
                view.overScrollMode = View.OVER_SCROLL_NEVER
                view.isNestedScrollingEnabled = true
                view.clipChildren = true
            }
        }

        controller.bindAppBarBehavior()
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                controller.bindAppBarBehavior()
            }

            override fun onViewDetachedFromWindow(v: View) {
                detach(view)
            }
        }
        attached[view] = Attachment(
            controller = controller,
            attachListener = attachListener,
            rvTouchListener = rvTouchListener,
            previousFlingListener = previousFlingListener,
            originalOverScrollMode = originalOverScrollMode,
        )
        view.addOnAttachStateChangeListener(attachListener)
    }

    fun detach(view: View) {
        val att = attached.remove(view) ?: return
        view.removeOnAttachStateChangeListener(att.attachListener)
        if (view is RecyclerView) {
            att.rvTouchListener?.let { view.removeOnItemTouchListener(it) }
            view.onFlingListener = att.previousFlingListener
            view.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
        }
        view.overScrollMode = att.originalOverScrollMode
        att.controller.dispose()
    }

    fun reacquireFromTouch(
        scopeRoot: View? = null,
        touchedHorizontalWidget: Boolean = false,
        downTime: Long = Long.MIN_VALUE,
    ) {
        for (att in attached.values) {
            if (isSameScope(att.controller.host, scopeRoot)) {
                att.controller.reacquireFromTouch(touchedHorizontalWidget, downTime)
            }
        }
    }

    fun onTouchUpOrCancel(scopeRoot: View? = null) {
        for (att in attached.values) {
            if (isSameScope(att.controller.host, scopeRoot)) {
                att.controller.release()
            }
        }
    }

    fun confirmHorizontalTakeoverClearTension(scopeRoot: View? = null) {
        for (att in attached.values) {
            if (isSameScope(att.controller.host, scopeRoot)) {
                att.controller.confirmHorizontalTakeoverClearTension()
            }
        }
    }

    /** Xoa ngay lap tuc do dan tren mot view cu the (vi du khi an/tat noi dung). */
    fun clearTension(view: View) {
        attached[view]?.controller?.confirmHorizontalTakeoverClearTension()
    }

    internal fun isEligibleScrollable(view: View): Boolean {
        if (view is NestedScrollView) {
            return true
        }
        if (view is RecyclerView) {
            if (XperiaTouchArbitrationController.isViewPager2Class(view.parent)) return false
            if (view.layoutManager?.canScrollHorizontally() == true) return false
            return true
        }
        return false
    }

    private fun isSameScope(host: View, scopeRoot: View?): Boolean {
        if (scopeRoot == null) return true
        if (host === scopeRoot) return true
        var p = host.parent
        while (p is View) {
            if (p === scopeRoot) return true
            p = p.parent
        }
        return host.rootView === scopeRoot.rootView
    }
}

internal class PhaseStretchController(
    val host: View,
    private val explicitAppBar: AppBarLayout?,
) : XperiaPhaseScrollOwner {
    private var state = XperiaStretchMath.TensionState()
    private var generation = 0L
    private var settleRunning = false
    private var settlePos = 0.0
    private var settleVel = 0.0
    private var settlePrev = 0.0
    private var settlePeriod = XperiaStretchMath.SettlePeriodNormal
    private var lastFrameNanos = -1L
    private var lastReacquireDownTime = Long.MIN_VALUE
    private var boundBehavior: XperiaAppBarScrollBehavior? = null

    var forwardingFling = false
        private set

    fun isSettling(): Boolean = settleRunning

    fun boundG(): Float = host.height.toFloat().coerceAtLeast(0f)

    fun peekVisualE(): Float = state.visualE

    private fun translationTarget(): View =
        if (host is NestedScrollView && host.childCount > 0) {
            host.getChildAt(0)
        } else {
            host
        }

    fun confirmHorizontalTakeoverClearTension() {
        generation += 1
        stopSettleFrames()
        state = XperiaStretchMath.TensionState()
        (host as? RecyclerView)?.stopScroll()
        publish()
    }

    fun handoffFling(velocityX: Int, velocityY: Int): Boolean {
        val rv = host as? RecyclerView ?: return false
        val velocityYInMiuix = -velocityY.toFloat()
        val pre = XperiaScrollPhase.flingPreConsumedY(state.visualE, velocityYInMiuix)
        if (pre == 0f) return false
        startSettle(velocityYInMiuix)
        val toChildY = -(velocityYInMiuix - pre).roundToInt()
        var childStarted = false
        if (velocityX != 0 || toChildY != 0) {
            forwardingFling = true
            try {
                childStarted = rv.fling(velocityX, toChildY)
            } finally {
                forwardingFling = false
            }
        }
        if (!childStarted) {
            rv.stopScroll()
        }
        return true
    }

    override fun onNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        if (host !is NestedScrollView) return false
        if (abs(state.visualE) <= XperiaStretchMath.VisualEpsilonPx) return false
        val velocityYInMiuix = -velocityY
        val pre = XperiaScrollPhase.flingPreConsumedY(state.visualE, velocityYInMiuix)
        if (pre == 0f) return false
        startSettle(velocityYInMiuix)
        return sign(velocityYInMiuix) == sign(state.visualE)
    }

    override fun onNestedFling(velocityY: Float, consumed: Boolean) {
        if (host !is NestedScrollView) return
        val velocityYInMiuix = -velocityY
        if (abs(state.visualE) > XperiaStretchMath.VisualEpsilonPx) {
            val leftover = if (consumed) 0f else velocityYInMiuix
            val settleVy = XperiaScrollPhase.flingSettleVelocity(state.visualE, leftover)
                ?: leftover
            startSettle(settleVy)
            return
        }
        if (!consumed && ((velocityYInMiuix > 0f && allowTopStretch()) ||
                (velocityYInMiuix < 0f && allowBottomStretch()))
        ) {
            val settleVy = XperiaScrollPhase.flingSettleVelocity(state.visualE, velocityYInMiuix)
                ?: velocityYInMiuix
            startSettle(settleVy)
        }
    }

    override fun onNestedTouchScrollStopped() {
        if (host is NestedScrollView) {
            release()
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!settleRunning) return
            val gen = generation
            val scale = animatorScale()
            if (scale <= 0f) {
                finishSettleZero(gen)
                return
            }
            if (lastFrameNanos < 0L) {
                lastFrameNanos = frameTimeNanos
                if (gen == generation && settleRunning) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
                return
            }
            val dtSec = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0)
                .coerceIn(0.001, 0.016)
            lastFrameNanos = frameTimeNanos
            val scaledDt = (dtSec / scale.toDouble().coerceAtLeast(0.01)).coerceIn(0.001, 0.016)
            val step = XperiaStretchMath.settleStep(settlePos, settleVel, scaledDt, settlePeriod)
            if (XperiaStretchMath.shouldClearSettle(step.first, step.second, settlePrev)) {
                finishSettleZero(gen)
                return
            }
            settlePrev = settlePos
            settlePos = step.first
            settleVel = step.second
            if (gen != generation) return
            state = XperiaStretchMath.TensionState(
                rawF = 0f,
                visualE = settlePos.toFloat(),
                unbound = abs(settlePos) > XperiaStretchMath.maxDragVisual(boundG()) +
                    XperiaStretchMath.VisualEpsilonPx,
            )
            publish()
            if (abs(settlePos) < XperiaStretchMath.VisualEpsilonPx &&
                abs(settleVel) < XperiaStretchMath.VisualEpsilonPx
            ) {
                finishSettleZero(gen)
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun findAppBar(): AppBarLayout? {
        if (explicitAppBar != null) return explicitAppBar
        var cur: Any? = host.parent
        while (cur is View) {
            if (cur is CoordinatorLayout) {
                for (i in 0 until cur.childCount) {
                    val child = cur.getChildAt(i)
                    if (child is AppBarLayout) return child
                }
            }
            cur = cur.parent
        }
        return null
    }

    fun bindAppBarBehavior() {
        val appBar = findAppBar() ?: return
        val lp = appBar.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val behavior = when (val existing = lp.behavior) {
            is XperiaAppBarScrollBehavior -> existing
            else -> XperiaAppBarScrollBehavior().also { lp.behavior = it }
        }
        behavior.attachPhaseOwner(this)
        boundBehavior = behavior
    }

    override fun liveCaps(): XperiaScrollPhase.Caps {
        val appBar = findAppBar()
        return XperiaScrollPhase.capsFromViewMetrics(
            appBarTop = boundBehavior?.topAndBottomOffset ?: appBar?.top ?: 0,
            totalScrollRange = appBar?.totalScrollRange ?: 0,
            contentUpRemaining = contentScrollUpRemaining(),
            contentDownRemaining = contentScrollDownRemaining(),
            tensionE = state.visualE,
            boundG = boundG(),
            tensionRawF = state.rawF,
            tensionUnbound = state.unbound,
        )
    }

    override fun commitTension(state: XperiaStretchMath.TensionState) {
        cancelSettleKeepTension(bumpGeneration = true)
        this.state = state
        publish()
    }

    fun reacquireFromTouch(
        touchedHorizontalWidget: Boolean,
        downTime: Long = Long.MIN_VALUE,
    ) {
        if (downTime != Long.MIN_VALUE && downTime == lastReacquireDownTime && !touchedHorizontalWidget) {
            return
        }
        if (downTime != Long.MIN_VALUE) {
            lastReacquireDownTime = downTime
        }
        val hadTension = abs(state.visualE) > XperiaStretchMath.VisualEpsilonPx || settleRunning
        generation += 1
        stopSettleFrames()
        ViewCompat.stopNestedScroll(host, ViewCompat.TYPE_NON_TOUCH)
        if (XperiaTouchAxisOwnership.shouldStopListFlingOnDown(hadTension, touchedHorizontalWidget)) {
            (host as? RecyclerView)?.stopScroll()
            ViewCompat.stopNestedScroll(host, ViewCompat.TYPE_TOUCH)
        }
        val g = boundG()
        if (abs(state.visualE) > XperiaStretchMath.VisualEpsilonPx && g > 0f && state.rawF == 0f) {
            state = XperiaStretchMath.reacquireAfterInterrupt(state.visualE, g)
        }
        settleRunning = false
    }

    fun contentScrollUpRemaining(): Float = when (host) {
        is RecyclerView -> {
            if (host.canScrollVertically(-1)) {
                host.computeVerticalScrollOffset().toFloat().coerceAtLeast(2f)
            } else {
                0f
            }
        }
        is NestedScrollView -> {
            if (host.canScrollVertically(-1)) {
                host.scrollY.toFloat().coerceAtLeast(2f)
            } else {
                0f
            }
        }
        else -> 0f
    }

    fun contentScrollDownRemaining(): Float = when (host) {
        is RecyclerView -> {
            if (!host.canScrollVertically(1)) {
                0f
            } else {
                val range = host.computeVerticalScrollRange() - host.computeVerticalScrollExtent()
                (range - host.computeVerticalScrollOffset()).toFloat().coerceAtLeast(2f)
            }
        }
        is NestedScrollView -> {
            if (!host.canScrollVertically(1) || host.childCount == 0) {
                0f
            } else {
                val childHeight = host.getChildAt(0).height
                val viewport = (host.height - host.paddingTop - host.paddingBottom).coerceAtLeast(0)
                val maxScroll = max(0, childHeight - viewport)
                (maxScroll - host.scrollY).toFloat().coerceAtLeast(2f)
            }
        }
        else -> 0f
    }

    fun allowTopStretch(): Boolean {
        val caps = liveCaps()
        return caps.canTopStretch || (!caps.canContentScroll && caps.headerFullyExpanded)
    }

    fun allowBottomStretch(): Boolean {
        val caps = liveCaps()
        return caps.canBottomStretch || !caps.canContentScroll
    }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(
            host.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ).coerceAtLeast(0f)

    fun consumePullPx(deltaPx: Float): Float {
        if (deltaPx == 0f) return 0f
        cancelSettleKeepTension(bumpGeneration = true)
        val g = boundG()
        if (g <= XperiaStretchMath.VisualEpsilonPx) return 0f

        val tensionSign = when {
            abs(state.visualE) > XperiaStretchMath.VisualEpsilonPx -> sign(state.visualE)
            abs(state.rawF) > XperiaStretchMath.VisualEpsilonPx -> sign(state.rawF)
            else -> 0f
        }
        if (tensionSign != 0f && sign(deltaPx) != tensionSign) {
            val (next, consumed) = XperiaStretchMath.reverseRelease(state, deltaPx, g)
            state = next
            publish()
            return consumed
        }
        val beforeF = state.rawF
        val beforeE = state.visualE
        val next = XperiaStretchMath.applyDelta(state, deltaPx, g)
        val actual = if (next.unbound || state.unbound) {
            next.visualE - beforeE
        } else {
            next.rawF - beforeF
        }
        state = next
        publish()
        return actual
    }

    fun absorb(velocity: Int, isTop: Boolean) {
        if (isTop && !allowTopStretch() && abs(state.visualE) <= XperiaStretchMath.VisualEpsilonPx) {
            return
        }
        if (!isTop && !allowBottomStretch() && abs(state.visualE) <= XperiaStretchMath.VisualEpsilonPx) {
            return
        }
        val signed = if (isTop) velocity.toFloat() else -velocity.toFloat()
        val settleVy = XperiaScrollPhase.flingSettleVelocity(state.visualE, signed)
            ?: signed
        startSettle(settleVy)
    }

    fun release() {
        if (!settleRunning) startSettle(0f)
    }

    private fun publish() {
        val target = translationTarget()
        target.translationY = state.visualE
        target.invalidate()
    }

    private fun cancelSettleKeepTension(bumpGeneration: Boolean) {
        if (bumpGeneration) generation += 1
        stopSettleFrames()
        val g = boundG()
        if (state.rawF == 0f && abs(state.visualE) > XperiaStretchMath.VisualEpsilonPx && g > 0f) {
            state = XperiaStretchMath.reacquireAfterInterrupt(state.visualE, g)
        }
    }

    private fun stopSettleFrames() {
        if (settleRunning) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
        settleRunning = false
        lastFrameNanos = -1L
    }

    private fun finishSettleZero(gen: Long) {
        if (gen != generation) return
        stopSettleFrames()
        state = XperiaStretchMath.TensionState()
        publish()
    }

    private fun startSettle(initialVelocity: Float) {
        generation += 1
        val gen = generation
        stopSettleFrames()

        val scale = animatorScale()
        if (scale == 0f) {
            state = XperiaStretchMath.TensionState()
            publish()
            return
        }
        if (abs(state.visualE) <= XperiaStretchMath.VisualEpsilonPx &&
            abs(initialVelocity) <= XperiaStretchMath.VisualEpsilonPx
        ) {
            state = XperiaStretchMath.TensionState()
            publish()
            return
        }

        settlePeriod = XperiaStretchMath.settlePeriodSeconds(initialVelocity)
        settlePos = state.visualE.toDouble()
        settleVel = initialVelocity.toDouble()
        settlePrev = settlePos
        state = XperiaStretchMath.TensionState(
            rawF = 0f,
            visualE = state.visualE,
            unbound = state.unbound,
        )
        settleRunning = true
        lastFrameNanos = -1L
        if (gen != generation) return
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun dispose() {
        generation += 1
        stopSettleFrames()
        // Chi go owner khi behavior van thuoc ve controller nay (tranh xoa nham fragment moi khi xoay man hinh)
        if (boundBehavior?.phaseOwnerOrNull() === this) {
            boundBehavior?.attachPhaseOwner(null)
        }
        boundBehavior = null
        state = XperiaStretchMath.TensionState()
        translationTarget().translationY = 0f
    }
}

private class PhaseGatedEdgeEffect(
    context: Context,
    private val controller: PhaseStretchController,
    private val isTop: Boolean,
) : EdgeEffect(context) {

    override fun getDistance(): Float {
        if (controller.isSettling()) return 0f
        val g = controller.boundG()
        if (g <= XperiaStretchMath.VisualEpsilonPx) return 0f
        val e = controller.peekVisualE()
        return when {
            isTop && e > XperiaStretchMath.VisualEpsilonPx -> (e / g).coerceIn(0f, 1f)
            !isTop && e < -XperiaStretchMath.VisualEpsilonPx -> ((-e) / g).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    override fun onPull(deltaDistance: Float) {
        onPullDistance(deltaDistance, 0.5f)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        onPullDistance(deltaDistance, displacement)
    }

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        val g = controller.boundG()
        if (g <= XperiaStretchMath.VisualEpsilonPx || deltaDistance == 0f) return 0f

        val increasing = deltaDistance > 0f
        if (increasing) {
            if (isTop && !controller.allowTopStretch()) return 0f
            if (!isTop && !controller.allowBottomStretch()) return 0f
        } else {
            if (getDistance() <= 0f) return 0f
        }

        val signedPx = if (isTop) deltaDistance * g else -deltaDistance * g
        val consumedPx = controller.consumePullPx(signedPx)
        if (consumedPx == 0f) return 0f
        val frac = (consumedPx / signedPx) * deltaDistance
        return frac.coerceIn(
            minOf(0f, deltaDistance),
            maxOf(0f, deltaDistance),
        )
    }

    override fun onRelease() {
        controller.release()
    }

    override fun onAbsorb(velocity: Int) {
        controller.absorb(velocity, isTop)
    }

    override fun draw(canvas: Canvas): Boolean = false

    override fun isFinished(): Boolean {
        val e = controller.peekVisualE()
        return if (isTop) {
            e <= XperiaStretchMath.VisualEpsilonPx
        } else {
            e >= -XperiaStretchMath.VisualEpsilonPx
        }
    }

    override fun finish() {
        // Settle/release quan ly vong doi, khong xoa giua chung
    }
}
