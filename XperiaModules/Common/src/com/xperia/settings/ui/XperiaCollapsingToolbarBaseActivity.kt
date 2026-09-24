/*
 * Copyright (C) 2026 XperiaLabs Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/**
 * Lop co so dung chung cho cac Activity SettingsLib cua Xperia.
 *
 * Tu dong gan XperiaMiuixHeaderController de sua loi vuot ngan va dung hieu ung tieu de Miuix.
 */
open class XperiaCollapsingToolbarBaseActivity : CollapsingToolbarBaseActivity() {

    private var miuixHeaderController: XperiaMiuixHeaderController? = null

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
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        miuixHeaderController?.onTitleChanged()
    }

    /** Doi mau tieu de tuy chinh (vi du accent man hinh BatteryMonitor). */
    fun setMiuixTitleColor(@ColorInt color: Int) {
        miuixHeaderController?.setTitleColor(color)
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
