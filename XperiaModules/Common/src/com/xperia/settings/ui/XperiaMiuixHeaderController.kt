/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.annotation.ColorInt
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bo dieu khien tieu de Miuix Folme Spring cho AppBarLayout + CollapsingToolbarLayout.
 *
 * Ap dung dung cho ca android.widget.Toolbar (SettingsLib) va MaterialToolbar/AppCompat Toolbar.
 */
class XperiaMiuixHeaderController private constructor(
    private val appBar: AppBarLayout,
    private val collapsingToolbar: CollapsingToolbarLayout,
    private val toolbar: ViewGroup,
    @ColorInt initialTitleColor: Int,
) {
    private var baseTitleColor: Int = initialTitleColor
    private var lastAppliedExpandedColor: Int? = null
    private var lastFraction: Float = 0f
    private var listenerAttached = false

    private val overlayView = SmallTitleOverlayView(collapsingToolbar.context)

    private var paintAlpha = 0f
    private var paintAlphaVelocity = 0f
    private var paintTranslationY = XperiaMiuixTitleMotion.TransitionDistancePx
    private var paintTranslationYVelocity = 0f

    private var targetAlpha = 0f
    private var targetTranslationY = XperiaMiuixTitleMotion.TransitionDistancePx
    private var isSmallTitleVisible = false

    private var initialOffsetApplied = false
    private var framePosted = false
    private var lastFrameNanos = 0L
    private var durationScale = 1f

    private val paintFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!collapsingToolbar.isAttachedToWindow) return
            if (!ValueAnimator.areAnimatorsEnabled()) {
                paintAlpha = targetAlpha
                paintAlphaVelocity = 0f
                paintTranslationY = targetTranslationY
                paintTranslationYVelocity = 0f
                overlayView.invalidate()
                return
            }
            val dt = if (lastFrameNanos == 0L) 0f else
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f / durationScale)
                    .coerceIn(0f, 0.064f)
            lastFrameNanos = frameTimeNanos

            val visible = isSmallTitleVisible
            val sampleAlpha = XperiaMiuixTitleMotion.alphaStep(
                paintAlpha,
                paintAlphaVelocity,
                visible,
                dt,
            )
            val sampleY = XperiaMiuixTitleMotion.translationYStep(
                paintTranslationY,
                paintTranslationYVelocity,
                visible,
                dt,
            )
            paintAlpha = sampleAlpha.value
            paintAlphaVelocity = sampleAlpha.velocity
            paintTranslationY = sampleY.value
            paintTranslationYVelocity = sampleY.velocity
            overlayView.invalidate()
            if (!sampleAlpha.settled || !sampleY.settled) {
                postPaintFrame()
            } else {
                lastFrameNanos = 0L
            }
        }
    }

    private val offsetListener = AppBarLayout.OnOffsetChangedListener { bar, offset ->
        val range = bar.totalScrollRange.toFloat()
        val fraction = XperiaMiuixTitleMotion.collapseFraction(offset.toFloat(), range)
        lastFraction = fraction
        applyExpandedTitleAlpha(fraction)

        val visible = XperiaMiuixTitleMotion.isSmallTitleVisible(fraction)
        val stateChanged = (visible != isSmallTitleVisible)
        isSmallTitleVisible = visible

        targetAlpha = if (visible) 1f else 0f
        targetTranslationY = if (visible) 0f else XperiaMiuixTitleMotion.TransitionDistancePx
        durationScale = ValueAnimator.getDurationScale().coerceAtLeast(0.01f)

        if (!initialOffsetApplied || !ValueAnimator.areAnimatorsEnabled()) {
            // Khoi tao trang thai theo offset thuc te ma khong chay animation luc mo man hinh
            paintAlpha = targetAlpha
            paintAlphaVelocity = 0f
            paintTranslationY = targetTranslationY
            paintTranslationYVelocity = 0f
            initialOffsetApplied = bar.totalScrollRange > 0
        } else if (stateChanged || paintAlpha != targetAlpha ||
            paintTranslationY != targetTranslationY ||
            paintAlphaVelocity != 0f || paintTranslationYVelocity != 0f
        ) {
            // Giu van toc dong hoc hien tai de retarget muot ma khi dao chieu cuon
            postPaintFrame()
        }
        overlayView.invalidate()
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            attachOffsetListener()
        }

        override fun onViewDetachedFromWindow(v: View) {
            detachOffsetListener()
        }
    }

    init {
        // Go co SNAP mac dinh (nguong 50%) cua Material de tranh nay nguoc trong vung 33%..49%;
        // XperiaAppBarScrollBehavior se tu settle theo huong cuon (hysteresis).
        val collapsingLp = collapsingToolbar.layoutParams as? AppBarLayout.LayoutParams
        if (collapsingLp != null) {
            val cleanedFlags = collapsingLp.scrollFlags and
                (AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP_MARGINS).inv()
            if (cleanedFlags != collapsingLp.scrollFlags) {
                collapsingLp.scrollFlags = cleanedFlags
                collapsingToolbar.layoutParams = collapsingLp
            }
        }
        collapsingToolbar.setCollapsedTitleTextColor(Color.TRANSPARENT)
        val toolbarLp = toolbar.layoutParams as? ViewGroup.MarginLayoutParams
        val overlayHeight = toolbar.layoutParams?.height?.takeIf { it > 0 }
            ?: ViewGroup.LayoutParams.WRAP_CONTENT
        collapsingToolbar.addView(
            overlayView,
            CollapsingToolbarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                overlayHeight,
            ).apply {
                gravity = Gravity.TOP
                if (toolbarLp != null) {
                    topMargin = toolbarLp.topMargin
                }
                // Ghim overlay cung collapseMode PIN voi toolbar de khoa toa do Y dong bo tung frame
                collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            },
        )
        collapsingToolbar.addOnAttachStateChangeListener(attachListener)
        if (appBar.parent is CoordinatorLayout) {
            // Giu AppBar o lop tren cung de overscroll translationY cua noi dung luon chui ben duoi
            appBar.bringToFront()
        }
        if (collapsingToolbar.isAttachedToWindow) {
            attachOffsetListener()
        }
    }

    private fun attachOffsetListener() {
        if (!listenerAttached) {
            appBar.addOnOffsetChangedListener(offsetListener)
            listenerAttached = true
        }
        val behavior = (appBar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior
            as? AppBarLayout.Behavior
        offsetListener.onOffsetChanged(appBar, behavior?.topAndBottomOffset ?: 0)
    }

    private fun detachOffsetListener() {
        if (listenerAttached) {
            appBar.removeOnOffsetChangedListener(offsetListener)
            listenerAttached = false
        }
        Choreographer.getInstance().removeFrameCallback(paintFrame)
        framePosted = false
        lastFrameNanos = 0L
        initialOffsetApplied = false
    }

    private fun postPaintFrame() {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(paintFrame)
    }

    private fun applyExpandedTitleAlpha(fraction: Float) {
        val largeAlpha = XperiaMiuixTitleMotion.largeTitleAlpha(fraction)
        val alphaInt = (Color.alpha(baseTitleColor) * largeAlpha).roundToInt().coerceIn(0, 255)
        val expandedArgb = ColorUtils.setAlphaComponent(baseTitleColor, alphaInt)
        if (expandedArgb != lastAppliedExpandedColor) {
            lastAppliedExpandedColor = expandedArgb
            collapsingToolbar.setExpandedTitleTextColor(ColorStateList.valueOf(expandedArgb))
        }
    }

    /** Cap nhat mau tieu de tuy chinh (vi du BatteryMonitor accent) nhung van an ban collapsed goc. */
    fun setTitleColor(@ColorInt color: Int) {
        baseTitleColor = color
        lastAppliedExpandedColor = null
        collapsingToolbar.setCollapsedTitleTextColor(Color.TRANSPARENT)
        applyExpandedTitleAlpha(lastFraction)
        overlayView.invalidate()
    }

    fun onTitleChanged() {
        overlayView.invalidate()
    }

    private inner class SmallTitleOverlayView(context: Context) : View(context) {
        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                22f,
                resources.displayMetrics,
            )
            typeface = resolveCollapsedTitleTypeface()
        }
        private val toolbarBounds = Rect()
        private val navigationBounds = Rect()

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setWillNotDraw(false)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val targetHeight = when {
                toolbar.measuredHeight > 0 -> toolbar.measuredHeight
                toolbar.height > 0 -> toolbar.height
                toolbar.layoutParams?.height?.let { it > 0 } == true -> toolbar.layoutParams.height
                else -> 0
            }
            if (targetHeight > 0) {
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY),
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val alpha = paintAlpha.coerceIn(0f, 1f)
            if (alpha <= 0f) return
            val text = collapsingToolbar.title
            if (text.isNullOrEmpty()) return

            toolbarBounds.set(0, 0, toolbar.width, toolbar.height)
            if (toolbarBounds.isEmpty) return
            collapsingToolbar.offsetDescendantRectToMyCoords(toolbar, toolbarBounds)
            toolbarBounds.offset(-left, -top)

            val navIcon = toolbarNavigationIcon(toolbar)
            var hasNavigation = false
            var navCenterX = toolbarBounds.exactCenterX()
            var navCenterY = toolbarBounds.exactCenterY()
            var fallbackButton: ImageButton? = null

            for (index in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(index)
                if (child is ImageButton && child.visibility == VISIBLE) {
                    if (navIcon != null && child.drawable === navIcon) {
                        navigationBounds.set(0, 0, child.width, child.height)
                        collapsingToolbar.offsetDescendantRectToMyCoords(child, navigationBounds)
                        navigationBounds.offset(-left, -top)
                        hasNavigation = true
                        navCenterX = navigationBounds.exactCenterX()
                        navCenterY = navigationBounds.exactCenterY()
                        break
                    } else if (fallbackButton == null) {
                        fallbackButton = child
                    }
                }
            }
            if (!hasNavigation && fallbackButton != null) {
                navigationBounds.set(0, 0, fallbackButton.width, fallbackButton.height)
                collapsingToolbar.offsetDescendantRectToMyCoords(fallbackButton, navigationBounds)
                navigationBounds.offset(-left, -top)
                hasNavigation = true
                navCenterX = navigationBounds.exactCenterX()
                navCenterY = navigationBounds.exactCenterY()
            }

            val density = resources.displayMetrics.density
            val backSizePx = 40f * density
            val gapPx = 22f * density
            val edgePx = 16f * density
            val endReservePx = computeEndReservePx(toolbar, edgePx)
            val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

            val bounds = XperiaMiuixTitleMotion.collapsedTitleHorizontalBounds(
                containerWidthPx = width.toFloat(),
                toolbarLeftPx = toolbarBounds.left.toFloat(),
                toolbarRightPx = toolbarBounds.right.toFloat(),
                navCenterXPx = navCenterX,
                hasNavigation = hasNavigation,
                backIconSizePx = backSizePx,
                gapPx = gapPx,
                contentInsetStartPx = max(edgePx, toolbarContentInsetStart(toolbar)),
                titleMarginStartPx = toolbarTitleMarginStart(toolbar),
                endReservePx = endReservePx,
                isRtl = isRtl,
            )

            titlePaint.color = baseTitleColor
            titlePaint.alpha = (Color.alpha(baseTitleColor) * alpha).roundToInt().coerceIn(0, 255)
            val label = TextUtils.ellipsize(
                text,
                titlePaint,
                bounds.availableWidth,
                TextUtils.TruncateAt.END,
            )
            val labelWidth = titlePaint.measureText(label, 0, label.length)
            val x = if (isRtl) {
                bounds.drawStartX - labelWidth
            } else {
                bounds.drawStartX
            }
            val baseline = navCenterY -
                (titlePaint.ascent() + titlePaint.descent()) / 2f +
                paintTranslationY

            val save = canvas.save()
            canvas.clipRect(toolbarBounds)
            canvas.drawText(label, 0, label.length, x, baseline, titlePaint)
            canvas.restoreToCount(save)
        }
    }

    companion object {
        fun attach(
            appBar: AppBarLayout,
            collapsingToolbar: CollapsingToolbarLayout,
            toolbar: ViewGroup,
            allowHeaderDrag: Boolean = false,
        ): XperiaMiuixHeaderController {
            (appBar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior =
                XperiaAppBarScrollBehavior(allowHeaderDrag)
            val defaultColor = resolveDefaultTitleColor(collapsingToolbar)
            return XperiaMiuixHeaderController(appBar, collapsingToolbar, toolbar, defaultColor)
        }

        @ColorInt
        private fun resolveDefaultTitleColor(view: View): Int {
            val fallback = resolveAttrColor(view.context, android.R.attr.textColorPrimary, Color.WHITE)
            return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorOnSurface,
                fallback,
            )
        }

        @ColorInt
        private fun resolveAttrColor(context: Context, attr: Int, fallback: Int): Int {
            val typed = TypedValue()
            if (!context.theme.resolveAttribute(attr, typed, true)) {
                return fallback
            }
            return if (typed.resourceId != 0) {
                context.getColor(typed.resourceId)
            } else {
                typed.data
            }
        }

        private fun resolveCollapsedTitleTypeface(): Typeface {
            val emphasized = Typeface.create("variable-title-large-emphasized", Typeface.NORMAL)
            return if (emphasized != Typeface.DEFAULT) {
                emphasized
            } else {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }

        private fun toolbarNavigationIcon(toolbar: ViewGroup): Drawable? = when (toolbar) {
            is android.widget.Toolbar -> toolbar.navigationIcon
            is androidx.appcompat.widget.Toolbar -> toolbar.navigationIcon
            else -> null
        }

        private fun toolbarContentInsetStart(toolbar: ViewGroup): Float = when (toolbar) {
            is android.widget.Toolbar -> toolbar.contentInsetStart.toFloat()
            is androidx.appcompat.widget.Toolbar -> toolbar.currentContentInsetStart.toFloat()
            else -> 0f
        }

        private fun toolbarTitleMarginStart(toolbar: ViewGroup): Float = when (toolbar) {
            is android.widget.Toolbar -> toolbar.titleMarginStart.toFloat()
            is androidx.appcompat.widget.Toolbar -> toolbar.titleMarginStart.toFloat()
            else -> 0f
        }

        private fun computeEndReservePx(toolbar: ViewGroup, edgePx: Float): Float {
            var reserve = when (toolbar) {
                is android.widget.Toolbar ->
                    max(edgePx, toolbar.contentInsetEnd.toFloat()) + toolbar.titleMarginEnd
                is androidx.appcompat.widget.Toolbar ->
                    max(edgePx, toolbar.currentContentInsetEnd.toFloat()) + toolbar.titleMarginEnd
                else -> edgePx
            }
            for (index in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(index)
                if (child.visibility == View.VISIBLE && child.width > 0 &&
                    (child is android.widget.ActionMenuView ||
                        child is androidx.appcompat.widget.ActionMenuView)
                ) {
                    reserve = max(reserve, child.width.toFloat() + edgePx)
                }
            }
            return reserve
        }
    }
}
