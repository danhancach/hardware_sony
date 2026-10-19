package com.sonyericsson.soundenhancement.backend

import android.content.Context
import android.media.AudioManager
import android.util.Log

// Dieu khien DSEE Ultimate / DSEE-HX
class DseeController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Doc trang thai DSEE
    fun isEnabled(): Boolean {
        return try {
            val param = audioManager.getParameters(AudioConstants.PARAM_DSEE_HX_STATE) ?: return false
            val parts = param.split("=")
            if (parts.size >= 2) {
                parts[1].trim() == "1"
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi doc DSEE: $e")
            false
        }
    }

    // Bat hoac tat DSEE
    fun setEnabled(enabled: Boolean) {
        if (isEnabled() == enabled) {
            return
        }
        val stateStr = if (enabled) "1" else "0"
        audioManager.setParameters("${AudioConstants.PARAM_DSEE_HX_STATE}=$stateStr")
    }
}
