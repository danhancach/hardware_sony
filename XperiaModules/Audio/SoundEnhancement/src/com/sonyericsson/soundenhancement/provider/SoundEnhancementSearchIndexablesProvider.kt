package com.sonyericsson.soundenhancement.provider

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.SearchIndexablesContract
import android.provider.SearchIndexablesProvider
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.ui.SoundSettingsActivity

/**
 * Cung cap chi muc tim kiem cho ung dung Settings cua Android
 */
open class SoundEnhancementSearchIndexablesProvider : SearchIndexablesProvider() {

    override fun onCreate(): Boolean = true

    override fun queryXmlResources(projection: Array<out String>?): Cursor {
        return MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS)
    }

    override fun queryRawData(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS)
        val context = context ?: return cursor

        // Muc cai dat chat luong am thanh chung (audiosettings)
        val audioSettingsRow = arrayOfNulls<Any>(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS.size)
        audioSettingsRow[0] = 1 // rank
        audioSettingsRow[1] = context.getString(R.string.sound_enhance_strings_audio_settings_txt) // title
        audioSettingsRow[2] = context.getString(R.string.sound_enhance_strings_audio_settings_description_txt) // summary
        audioSettingsRow[9] = "android.intent.action.MAIN" // intent action
        audioSettingsRow[10] = context.packageName // intent target package
        audioSettingsRow[11] = SoundSettingsActivity::class.java.name // intent target class
        audioSettingsRow[12] = "audiosettings" // key
        cursor.addRow(audioSettingsRow)

        // Muc Dolby Sound
        val dolbyRow = arrayOfNulls<Any>(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS.size)
        dolbyRow[0] = 2
        dolbyRow[1] = context.getString(R.string.sound_enhance_strings_dolby_sound_txt)
        dolbyRow[2] = context.getString(R.string.sound_enhance_strings_dolby_sound_summary_txt)
        dolbyRow[9] = "android.intent.action.MAIN"
        dolbyRow[10] = context.packageName
        dolbyRow[11] = SoundSettingsActivity::class.java.name
        dolbyRow[12] = "dolbyaudiosettings"
        cursor.addRow(dolbyRow)

        // Muc DSEE Ultimate
        val dseeRow = arrayOfNulls<Any>(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS.size)
        dseeRow[0] = 3
        dseeRow[1] = context.getString(R.string.sound_enhance_strings_setting_dsee_ultimate_txt)
        dseeRow[2] = context.getString(R.string.sound_enhance_strings_setting_dsee_ultimate_summary_txt)
        dseeRow[9] = "android.intent.action.MAIN"
        dseeRow[10] = context.packageName
        dseeRow[11] = SoundSettingsActivity::class.java.name
        dseeRow[12] = "dsee_ultimate"
        cursor.addRow(dseeRow)

        // Muc 360 Reality Audio Upmix
        val spatialRow = arrayOfNulls<Any>(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS.size)
        spatialRow[0] = 4
        spatialRow[1] = context.getString(R.string.sound_enhance_strings_upmix_txt)
        spatialRow[2] = context.getString(R.string.sound_enhance_strings_upmix_summary_txt)
        spatialRow[9] = "android.intent.action.MAIN"
        spatialRow[10] = context.packageName
        spatialRow[11] = SoundSettingsActivity::class.java.name
        spatialRow[12] = "spatial_sound"
        cursor.addRow(spatialRow)

        // Muc Giam tieng on gio (Wind Noise Reduction)
        val windRow = arrayOfNulls<Any>(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS.size)
        windRow[0] = 5
        windRow[1] = context.getString(R.string.sound_enhance_strings_setting_wind_noise_reduction_txt)
        windRow[2] = context.getString(R.string.sound_enhance_strings_setting_wind_noise_reduction_summary_txt)
        windRow[9] = "android.intent.action.MAIN"
        windRow[10] = context.packageName
        windRow[11] = SoundSettingsActivity::class.java.name
        windRow[12] = "audio_recording_setting"
        cursor.addRow(windRow)

        return cursor
    }

    override fun queryNonIndexableKeys(projection: Array<out String>?): Cursor {
        return MatrixCursor(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS)
    }
}
