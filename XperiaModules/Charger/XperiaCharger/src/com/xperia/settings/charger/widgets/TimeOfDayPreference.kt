/*
 * Copyright (C) 2025 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.charger.widgets

import android.content.Context
import android.content.DialogInterface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.xperia.settings.charger.ChargerUtils
import com.xperia.settings.charger.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Preference chon gio trong ngay (second-of-day). */
class TimeOfDayPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private var localTime: LocalTime = LocalTime.MIDNIGHT
    private val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        localTime = LocalTime.ofSecondOfDay(readSeconds().toLong())
        summary = formatSummary()
        super.onBindViewHolder(holder)
    }

    override fun onClick() {
        localTime = LocalTime.ofSecondOfDay(readSeconds().toLong())
        val view = View.inflate(context, R.layout.dialog_time_of_day, null)
        val timePicker = view.findViewById<TimePicker>(R.id.time_picker)
        timePicker.setIs24HourView(DateFormat.is24HourFormat(context))
        timePicker.hour = localTime.hour
        timePicker.minute = localTime.minute

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.charger_dlg_ok) { _: DialogInterface, _: Int ->
                localTime = LocalTime.of(timePicker.hour, timePicker.minute)
                writeSeconds(localTime.toSecondOfDay())
                summary = formatSummary()
                callChangeListener(localTime.toSecondOfDay())
            }
            .setNegativeButton(R.string.charger_dlg_cancel, null)
            .show()
    }

    fun setValue(secondOfDay: Int) {
        localTime = LocalTime.ofSecondOfDay(secondOfDay.coerceIn(0, 24 * 3600 - 1).toLong())
        summary = formatSummary()
    }

    private fun formatSummary(): String {
        val template = if (key == "device_charging_start_time") {
            R.string.charger_start_time_summary
        } else {
            R.string.charger_target_time_summary
        }
        return context.getString(template, localTime.format(formatter))
    }

    private fun readSeconds(): Int {
        val utils = ChargerUtils(context)
        return if (key == "device_charging_start_time") {
            utils.startTimeSeconds
        } else {
            utils.targetTimeSeconds
        }
    }

    private fun writeSeconds(seconds: Int) {
        val utils = ChargerUtils(context)
        if (key == "device_charging_start_time") {
            utils.startTimeSeconds = seconds
        } else {
            utils.targetTimeSeconds = seconds
        }
    }
}
