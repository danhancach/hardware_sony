/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.fragment.app.Fragment

class DisplaySettingsLandscapeFragment : Fragment() {
    private var previewHelper: DisplayPreviewHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.display_settings_landscape, container, false)
        val previewContainer = root.findViewById<ViewGroup>(R.id.preview_container)
        val preview = inflater.inflate(R.layout.creator_mode_preview, previewContainer, false)
        preview.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        previewContainer.addView(preview)

        previewHelper = DisplayPreviewHelper.setup(preview, layoutInflater, requireContext())
        previewHelper?.restorePageIndex(savedInstanceState)

        if (childFragmentManager.findFragmentById(R.id.controls_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.controls_container, DisplaySettingsControlsFragment())
                .commitNow()
        }

        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        previewHelper?.savePageIndex(outState)
    }
}
