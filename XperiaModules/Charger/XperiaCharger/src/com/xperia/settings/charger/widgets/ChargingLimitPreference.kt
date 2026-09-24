/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.SliderPreference
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.xperia.settings.charger.R

/**
 * Slider gioi han sac: chi hien % o goc tren phai, khong label 50/100 hay bubble thumb.
 */
class ChargingLimitPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SliderPreference(context, attrs, defStyleAttr) {

    private var valueView: TextView? = null

    init {
        setShowSliderValue(false)
        setUpdatesContinuously(true)
        setTickVisible(false)
        setSliderIncrement(1)
        setExtraChangeListener(Slider.OnChangeListener { _, value, _ ->
            updateValueLabel(value.toInt())
        })
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        slider?.setLabelBehavior(LabelFormatter.LABEL_GONE)

        holder.findViewById(com.android.settingslib.widget.preference.slider.R.id.label_frame)
            ?.visibility = View.GONE

        val title = holder.findViewById(android.R.id.title) as? TextView
        val summary = holder.findViewById(android.R.id.summary) as? TextView
        if (title != null && summary != null) {
            val lp = summary.layoutParams as? RelativeLayout.LayoutParams
            if (lp != null) {
                lp.removeRule(RelativeLayout.BELOW)
                lp.removeRule(RelativeLayout.ALIGN_START)
                lp.removeRule(RelativeLayout.ALIGN_LEFT)
                lp.addRule(RelativeLayout.ALIGN_PARENT_END)
                lp.addRule(RelativeLayout.ALIGN_TOP, android.R.id.title)
                lp.addRule(RelativeLayout.ALIGN_BOTTOM, android.R.id.title)
                summary.layoutParams = lp
            }
            summary.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            summary.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            summary.maxLines = 1
            summary.visibility = View.VISIBLE
            valueView = summary

            // Slider nam duoi title (khong con summary o duoi)
            holder.findViewById(com.android.settingslib.widget.preference.slider.R.id.slider_frame)
                ?.let { frame ->
                    val flp = frame.layoutParams as? RelativeLayout.LayoutParams ?: return@let
                    flp.removeRule(RelativeLayout.BELOW)
                    flp.addRule(RelativeLayout.BELOW, android.R.id.title)
                    flp.removeRule(RelativeLayout.ALIGN_START)
                    flp.addRule(RelativeLayout.ALIGN_PARENT_START)
                    frame.layoutParams = flp
                }
        }

        updateValueLabel(value)
    }

    override fun setValue(sliderValue: Int) {
        super.setValue(sliderValue)
        updateValueLabel(sliderValue)
    }

    private fun updateValueLabel(percent: Int) {
        valueView?.text = context.getString(R.string.charger_limit_value_format, percent)
    }
}
