package com.sonyericsson.soundenhancement

import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sonyericsson.soundenhancement.backend.AudioConstants
import com.sonyericsson.soundenhancement.backend.SpeakerPortraitMonoStore

// Tile the hien preference da luu, khong phu thuoc goc xoay hay route hien tai.
class SpeakerPortraitMonoTileService : TileService() {
    private lateinit var store: SpeakerPortraitMonoStore
    private var observer: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        store = SpeakerPortraitMonoStore(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        if (observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    refreshTile()
                }
            }
            contentResolver.registerContentObserver(
                SpeakerPortraitMonoStore.settingsUri(),
                false,
                observer!!
            )
        }
        refreshTile()
    }

    override fun onStopListening() {
        unregisterObserver()
        super.onStopListening()
    }

    override fun onDestroy() {
        unregisterObserver()
        super.onDestroy()
    }

    private fun unregisterObserver() {
        observer?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                android.util.Log.w(AudioConstants.TAG, "Bo observer tile mono: $e")
            }
        }
        observer = null
    }

    override fun onClick() {
        val current = store.isEnabled()
        if (!store.setEnabled(!current)) {
            refreshTile()
            return
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = SpeakerPortraitMonoStore.readEnabled(contentResolver)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this,
            if (enabled) R.drawable.ic_speaker_portrait_mono else R.drawable.ic_speaker_stereo
        )
        tile.label = getString(R.string.sound_enhance_speaker_portrait_mono_tile)
        tile.subtitle = getString(
            if (enabled) {
                R.string.sound_enhance_speaker_portrait_mono_on
            } else {
                R.string.sound_enhance_speaker_portrait_mono_off
            }
        )
        tile.contentDescription = "${tile.label}, ${tile.subtitle}"
        tile.updateTile()
    }
}
