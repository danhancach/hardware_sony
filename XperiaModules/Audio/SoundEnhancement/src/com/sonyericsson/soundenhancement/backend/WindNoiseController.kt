package com.sonyericsson.soundenhancement.backend

import android.content.Context
import android.media.AudioManager
import android.util.Log

// Dieu khien tinh nang giam tieng on gio khi ghi am (Wind Noise Reduction)
class WindNoiseController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isSupported(): Boolean {
        return try {
            val state = audioManager.getParameters(AudioConstants.PARAM_WIND_NR_SUPPORTED) ?: return false
            val parts = state.split("=")
            if (parts.size >= 2) {
                parts[1].trim() == "1"
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi kiem tra ho tro Wind NR: $e")
            false
        }
    }

    fun isEnabled(): Boolean {
        return try {
            val state = audioManager.getParameters(AudioConstants.PARAM_WIND_NR_ENABLED) ?: return false
            val parts = state.split("=")
            if (parts.size >= 2) {
                parts[1].trim() == "1"
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi doc Wind NR: $e")
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        val stateStr = if (enabled) "1" else "0"
        audioManager.setParameters("${AudioConstants.PARAM_WIND_NR_ENABLED}=$stateStr")
    }
}
