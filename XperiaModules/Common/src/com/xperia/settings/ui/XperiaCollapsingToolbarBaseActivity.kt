/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/**
 * Lop co so dung chung cho cac Activity SettingsLib cua Xperia.
 *
 * Tu dong gan XperiaMiuixHeaderController, XperiaMiuixScrollCoordinator va
 * bo phan xu vuot cham XperiaTouchArbitrationController cho toan bo man hinh con.
 */
open class XperiaCollapsingToolbarBaseActivity : CollapsingToolbarBaseActivity() {

    private var miuixHeaderController: XperiaMiuixHeaderController? = null
    private var touchArbitration: XperiaTouchArbitrationController? = null
    private var fragmentCallbacks: FragmentManager.FragmentLifecycleCallbacks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = appBarLayout
        val collapsing = collapsingToolbarLayout
        val toolbar = findViewById<ViewGroup>(
            com.android.settingslib.collapsingtoolbar.R.id.action_bar,
        ) ?: findViewById<ViewGroup>(
            com.android.settingslib.collapsingtoolbar.R.id.support_action_bar,
        )
        if (bar != null && collapsing != null && toolbar != null) {
            miuixHeaderController = XperiaMiuixHeaderController.attach(
                appBar = bar,
                collapsingToolbar = collapsing,
                toolbar = toolbar,
                allowHeaderDrag = false,
            )
        }
        touchArbitration = XperiaTouchArbitrationController(this) {
            findViewById(com.android.settingslib.collapsingtoolbar.R.id.content_frame)
        }
        val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?,
            ) {
                attachMiuixScrollables(v)
                v.post {
                    if (!isDestroyed && v.isAttachedToWindow) {
                        attachMiuixScrollables(v)
                    }
                }
            }
        }
        fragmentCallbacks = callbacks
        supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
    }

    override fun onDestroy() {
        fragmentCallbacks?.let { supportFragmentManager.unregisterFragmentLifecycleCallbacks(it) }
        fragmentCallbacks = null
        touchArbitration = null
        super.onDestroy()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        attachMiuixScrollablesInContentFrame()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        attachMiuixScrollablesInContentFrame()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        attachMiuixScrollablesInContentFrame()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val arbitration = touchArbitration
        return if (arbitration != null) {
            arbitration.dispatch(ev) { super.dispatchTouchEvent(it) }
        } else {
            super.dispatchTouchEvent(ev)
        }
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        miuixHeaderController?.onTitleChanged()
    }

    /** Doi mau tieu de tuy chinh (vi du accent man hinh BatteryMonitor). */
    fun setMiuixTitleColor(@ColorInt color: Int) {
        miuixHeaderController?.setTitleColor(color)
    }

    private fun attachMiuixScrollablesInContentFrame() {
        val contentFrame = findViewById<View>(
            com.android.settingslib.collapsingtoolbar.R.id.content_frame,
        ) ?: return
        attachMiuixScrollables(contentFrame)
    }

    private fun attachMiuixScrollables(root: View) {
        XperiaMiuixScrollCoordinator.attachInHierarchy(root, appBarLayout)
    }

    /** Giu khoang dem day cho view cuon trong vung noi dung SettingsLib. */
    protected fun applyScrollableBottomInsets(scrollable: View, extraBottomDp: Int = 16) {
        val initialBottom = scrollable.paddingBottom
        val extraPx = (extraBottomDp * scrollable.resources.displayMetrics.density).toInt()
        scrollable.setPadding(
            scrollable.paddingLeft,
            scrollable.paddingTop,
            scrollable.paddingRight,
            initialBottom + extraPx,
        )
        ViewCompat.setOnApplyWindowInsetsListener(scrollable) { view, windowInsets ->
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialBottom + navInsets.bottom + extraPx,
            )
            windowInsets
        }
        if (scrollable.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(scrollable)
        }
    }
}
