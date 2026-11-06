package com.sonyericsson.soundenhancement.ui

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.service.quicksettings.TileService
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
                    SoundSettingsFragment.newInstance(isQsTileSpeakerMonoIntent(intent)),
                    TAG,
                )
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isQsTileSpeakerMonoIntent(intent)) {
            (supportFragmentManager.findFragmentByTag(TAG) as? SoundSettingsFragment)
                ?.scrollToSpeakerPortraitMono()
        }
    }

    private fun isQsTileSpeakerMonoIntent(targetIntent: Intent?): Boolean =
        targetIntent?.action == TileService.ACTION_QS_TILE_PREFERENCES

    companion object {
        private const val TAG = "SoundSettingsFragment"
    }
}
