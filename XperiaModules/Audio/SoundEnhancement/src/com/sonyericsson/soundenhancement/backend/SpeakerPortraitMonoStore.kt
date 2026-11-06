package com.sonyericsson.soundenhancement.backend

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import android.util.Log

// Mot key Global dung chung cho UI, QS va AudioService. Khong dung SharedPreferences.
class SpeakerPortraitMonoStore(private val context: Context) {

    fun isEnabled(): Boolean = readEnabled(context.contentResolver)

    // false neu ghi that bai. Caller phai doc lai trang thai that.
    fun setEnabled(enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                KEY_SPEAKER_PORTRAIT_MONO,
                if (enabled) 1 else 0
            )
        } catch (e: SecurityException) {
            Log.w(AudioConstants.TAG, "Khong ghi duoc sony_speaker_portrait_mono: $e")
            false
        }
    }

    companion object {
        // Key Settings.Global dung chung; khong phu thuoc field framework custom.
        const val KEY_SPEAKER_PORTRAIT_MONO = "sony_speaker_portrait_mono"
        const val DEFAULT_ENABLED = 1

        fun settingsUri() = Settings.Global.getUriFor(KEY_SPEAKER_PORTRAIT_MONO)

        fun readEnabled(resolver: ContentResolver): Boolean {
            return Settings.Global.getInt(resolver, KEY_SPEAKER_PORTRAIT_MONO, DEFAULT_ENABLED) == 1
        }
    }
}
