package com.sonyericsson.soundenhancement.ui

import android.media.AudioManager
import android.os.Bundle
import com.sonyericsson.soundenhancement.R
import com.xperia.settings.ui.XperiaCollapsingToolbarBaseActivity

// Man hinh chinh Cai dat am thanh Sony dung chung SettingsBasePreferenceFragment voi menu Xperia
open class SoundSettingsActivity : XperiaCollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.sound_enhance_audio_settings_title)
        volumeControlStream = AudioManager.STREAM_MUSIC

        val contentFrame = com.android.settingslib.collapsingtoolbar.R.id.content_frame
        if (supportFragmentManager.findFragmentById(contentFrame) == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    contentFrame,
                    SoundSettingsFragment(),
                    TAG,
                )
                .commit()
        }
    }

    companion object {
        private const val TAG = "SoundSettingsFragment"
    }
}
