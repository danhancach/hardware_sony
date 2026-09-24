/*
 * Copyright (C) 2024 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.display

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

import com.android.settingslib.widget.LayoutPreference

import com.xperia.settings.display.R

class DisplayPreviewHelper private constructor(
    private val previewHost: PreviewHost,
    private val pageList: ArrayList<View>,
    private val viewPagerImages: Array<View?>,
    private val dotIndicators: Array<ImageView?>,
) {
    val viewPager: ViewPager = previewHost.findViewById(R.id.viewpager)

    fun restorePageIndex(savedInstanceState: Bundle?) {
        val pageIndex = savedInstanceState?.getInt(PAGE_VIEWER_SELECTION_INDEX, 0) ?: 0
        viewPager.setCurrentItem(pageIndex, false)
        updateIndicator(pageIndex)
    }

    fun savePageIndex(outState: Bundle) {
        outState.putInt(PAGE_VIEWER_SELECTION_INDEX, viewPager.currentItem)
    }

    private fun updateIndicator(position: Int) {
        for (i in pageList.indices) {
            if (position == i) {
                dotIndicators[i]!!.setBackgroundResource(R.drawable.ic_creator_mode_indicator_focused)
                viewPagerImages[i]!!.visibility = View.VISIBLE
            } else {
                dotIndicators[i]!!.setBackgroundResource(R.drawable.ic_creator_mode_indicator_unfocused)
                viewPagerImages[i]!!.visibility = View.INVISIBLE
            }
        }

        val arrowPrevious = previewHost.findViewById<View>(R.id.arrow_previous)
        val arrowNext = previewHost.findViewById<View>(R.id.arrow_next)
        when (position) {
            0 -> {
                arrowPrevious.visibility = View.INVISIBLE
                arrowNext.visibility = View.VISIBLE
            }
            pageList.size - 1 -> {
                arrowPrevious.visibility = View.VISIBLE
                arrowNext.visibility = View.INVISIBLE
            }
            else -> {
                arrowPrevious.visibility = View.VISIBLE
                arrowNext.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val DOT_INDICATOR_SIZE = 12
        private const val DOT_INDICATOR_LEFT_PADDING = 6
        private const val DOT_INDICATOR_RIGHT_PADDING = 6
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

            val viewPager = previewHost.findViewById<ViewPager>(R.id.viewpager)
            val viewPagerImages = arrayOfNulls<View>(pageLayouts.size)
            val pageList = ArrayList<View>(pageLayouts.size)

            for (idx in pageLayouts.indices) {
                viewPagerImages[idx] = layoutInflater.inflate(pageLayouts[idx], null)
                pageList.add(viewPagerImages[idx]!!)
            }

            viewPager.adapter = ColorPagerAdapter(pageList)

            previewHost.findViewById<View>(R.id.arrow_previous)?.setOnClickListener {
                viewPager.setCurrentItem(viewPager.currentItem - 1, true)
            }
            previewHost.findViewById<View>(R.id.arrow_next)?.setOnClickListener {
                viewPager.setCurrentItem(viewPager.currentItem + 1, true)
            }

            val viewGroup = previewHost.findViewById<ViewGroup>(R.id.viewGroup)
            val dotIndicators = arrayOfNulls<ImageView>(pageList.size)
            for (i in pageList.indices) {
                val imageView = ImageView(context)
                val lp = ViewGroup.MarginLayoutParams(DOT_INDICATOR_SIZE, DOT_INDICATOR_SIZE)
                lp.setMargins(DOT_INDICATOR_LEFT_PADDING, 0, DOT_INDICATOR_RIGHT_PADDING, 0)
                imageView.layoutParams = lp
                dotIndicators[i] = imageView
                viewGroup.addView(dotIndicators[i])
            }

            val helper = DisplayPreviewHelper(previewHost, pageList, viewPagerImages, dotIndicators)
            viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int,
                ) {
                    if (positionOffset != 0f) {
                        for (idx in pageList.indices) {
                            val visible = idx == position || idx == position + 1
                            viewPagerImages[idx]!!.visibility =
                                if (visible) View.VISIBLE else View.INVISIBLE
                        }
                    } else {
                        val descriptionRes = if (position == 0) {
                            R.string.creator_mode_content_description
                        } else {
                            R.string.xreality_mode_content_description
                        }
                        viewPagerImages[position]!!.contentDescription =
                            context.getString(descriptionRes)
                        helper.updateIndicator(position)
                    }
                }

                override fun onPageSelected(position: Int) {}

                override fun onPageScrollStateChanged(state: Int) {}
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

    private class ColorPagerAdapter(
        private val pageViewList: ArrayList<View>,
    ) : PagerAdapter() {
        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(pageViewList[position])
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val page = pageViewList[position]
            container.addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            return page
        }

        override fun getCount(): Int = pageViewList.size

        override fun isViewFromObject(view: View, `object`: Any): Boolean = `object` == view
    }
}
