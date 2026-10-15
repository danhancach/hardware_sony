/*
 * Copyright (C) 2024-2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

import com.android.settingslib.widget.LayoutPreference

import com.xperia.settings.display.R

class DisplayPreviewHelper private constructor(
    private val previewHost: PreviewHost,
    private val pageCount: Int,
    private val dotIndicators: Array<ImageView?>,
) {
    // Nang cap len ViewPager2 thay cho ViewPager1 cu
    val viewPager: ViewPager2 = previewHost.findViewById(R.id.viewpager)

    fun restorePageIndex(savedInstanceState: Bundle?) {
        val pageIndex = savedInstanceState?.getInt(PAGE_VIEWER_SELECTION_INDEX, 0) ?: 0
        viewPager.setCurrentItem(pageIndex, false)
        updateIndicator(pageIndex)
    }

    fun savePageIndex(outState: Bundle) {
        outState.putInt(PAGE_VIEWER_SELECTION_INDEX, viewPager.currentItem)
    }

    private fun updateIndicator(position: Int) {
        for (i in dotIndicators.indices) {
            if (position == i) {
                dotIndicators[i]?.setBackgroundResource(R.drawable.ic_creator_mode_indicator_focused)
            } else {
                dotIndicators[i]?.setBackgroundResource(R.drawable.ic_creator_mode_indicator_unfocused)
            }
        }

        val arrowPrevious = previewHost.findViewById<View>(R.id.arrow_previous)
        val arrowNext = previewHost.findViewById<View>(R.id.arrow_next)
        when (position) {
            0 -> {
                arrowPrevious?.visibility = View.INVISIBLE
                arrowNext?.visibility = View.VISIBLE
            }
            pageCount - 1 -> {
                arrowPrevious?.visibility = View.VISIBLE
                arrowNext?.visibility = View.INVISIBLE
            }
            else -> {
                arrowPrevious?.visibility = View.VISIBLE
                arrowNext?.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val PAGE_VIEWER_SELECTION_INDEX = "page_viewer_selection_index"

        fun setup(
            previewRoot: View,
            layoutInflater: LayoutInflater,
            context: Context,
        ): DisplayPreviewHelper {
            return setup(ViewPreviewHost(previewRoot), layoutInflater, context)
        }

        fun setup(
            preview: LayoutPreference,
            layoutInflater: LayoutInflater,
            context: Context,
        ): DisplayPreviewHelper {
            return setup(LayoutPreferencePreviewHost(preview), layoutInflater, context)
        }

        private fun setup(
            previewHost: PreviewHost,
            layoutInflater: LayoutInflater,
            context: Context,
        ): DisplayPreviewHelper {
            val pageLayouts = arrayListOf(
                R.layout.creator_mode_view1,
                R.layout.creator_mode_view2,
            )

            val viewPager = previewHost.findViewById<ViewPager2>(R.id.viewpager)
            viewPager.adapter = ColorPagerAdapter(pageLayouts)

            previewHost.findViewById<View>(R.id.arrow_previous)?.setOnClickListener {
                if (viewPager.currentItem > 0) {
                    viewPager.setCurrentItem(viewPager.currentItem - 1, true)
                }
            }
            previewHost.findViewById<View>(R.id.arrow_next)?.setOnClickListener {
                if (viewPager.currentItem < pageLayouts.size - 1) {
                    viewPager.setCurrentItem(viewPager.currentItem + 1, true)
                }
            }

            // Su dung dimen dp chuan hoa, khong dung pixel cung
            val dotSize = context.resources.getDimensionPixelSize(R.dimen.creator_mode_dot_size)
            val dotMargin = context.resources.getDimensionPixelSize(R.dimen.creator_mode_dot_margin)

            val viewGroup = previewHost.findViewById<ViewGroup>(R.id.viewGroup)
            viewGroup.removeAllViews()
            val dotIndicators = arrayOfNulls<ImageView>(pageLayouts.size)
            for (i in pageLayouts.indices) {
                val imageView = ImageView(context)
                val lp = ViewGroup.MarginLayoutParams(dotSize, dotSize)
                lp.setMargins(dotMargin, 0, dotMargin, 0)
                imageView.layoutParams = lp
                dotIndicators[i] = imageView
                viewGroup.addView(dotIndicators[i])
            }

            val helper = DisplayPreviewHelper(previewHost, pageLayouts.size, dotIndicators)
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    helper.updateIndicator(position)
                }
            })

            helper.updateIndicator(viewPager.currentItem)
            return helper
        }
    }

    private interface PreviewHost {
        fun <T : View> findViewById(id: Int): T
    }

    private class ViewPreviewHost(private val root: View) : PreviewHost {
        @Suppress("UNCHECKED_CAST")
        override fun <T : View> findViewById(id: Int): T = root.findViewById(id)
    }

    private class LayoutPreferencePreviewHost(
        private val preview: LayoutPreference,
    ) : PreviewHost {
        override fun <T : View> findViewById(id: Int): T = preview.findViewById(id)
    }

    // Adapter RecyclerView cho ViewPager2
    private class ColorPagerAdapter(
        private val pageLayouts: List<Int>,
    ) : RecyclerView.Adapter<ColorPagerAdapter.PageViewHolder>() {

        class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(pageLayouts[viewType], parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val descriptionRes = if (position == 0) {
                R.string.creator_mode_content_description
            } else {
                R.string.xreality_mode_content_description
            }
            holder.itemView.contentDescription = holder.itemView.context.getString(descriptionRes)
        }

        override fun getItemCount(): Int = pageLayouts.size

        override fun getItemViewType(position: Int): Int = position
    }
}
