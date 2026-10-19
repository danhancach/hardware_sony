package com.sonyericsson.soundenhancement.backend

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.UserHandle
import android.util.Log

// Dieu khien 360 Reality Audio va 360 Upmix
class SpatialAudioController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = context.getSharedPreferences("sound_enhancement_prefs", Context.MODE_PRIVATE)

    companion object {
        const val UPMIX_PROFILE_MUSIC = 1
        const val UPMIX_PROFILE_CINEMA = 2
        const val UPMIX_PROFILE_AUTO = 3
        private const val KEY_CACHED_UPMIX_PROFILE = "cached_upmix_profile"
    }

    // Doc trang thai 360 Upmix headphone tu AudioManager parameter
    // Gia tri parameter: 0 = tat, 1 = bat (music), 2 = bat (cinema), 3 = bat (auto)
    fun isEnabled(): Boolean {
        return try {
            val param = audioManager.getParameters(AudioConstants.PARAM_360RA_UPMIX_HEADPHONE) ?: return false
            val parts = param.split("=")
            if (parts.size >= 2) {
                val value = parts[1].trim().toIntOrNull() ?: 0
                value > 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi doc 360 Upmix state: $e")
            false
        }
    }

    // Doc profile 360 Upmix hien tai (1 = Music, 2 = Cinema, 3 = Auto)
    fun getProfile(): Int {
        return try {
            val param = audioManager.getParameters(AudioConstants.PARAM_360RA_UPMIX_HEADPHONE)
            val parts = param?.split("=")
            if (parts != null && parts.size >= 2) {
                val value = parts[1].trim().toIntOrNull() ?: 0
                if (value in 1..3) {
                    prefs.edit().putInt(KEY_CACHED_UPMIX_PROFILE, value).apply()
                    return value
                }
            }
            prefs.getInt(KEY_CACHED_UPMIX_PROFILE, UPMIX_PROFILE_AUTO)
        } catch (e: Exception) {
            prefs.getInt(KEY_CACHED_UPMIX_PROFILE, UPMIX_PROFILE_AUTO)
        }
    }

    // Luu va cap nhat profile 360 Upmix
    fun setProfile(profile: Int) {
        val targetProfile = if (profile in 1..3) profile else UPMIX_PROFILE_AUTO
        prefs.edit().putInt(KEY_CACHED_UPMIX_PROFILE, targetProfile).apply()
        setEnabled(isEnabled(), targetProfile)
    }

    // Bat hoac tat 360 Upmix headphone va dong bo voi SystemMainService
    fun setEnabled(enabled: Boolean, profile: Int = getProfile()) {
        val targetProfile = if (profile in 1..3) profile else getProfile()
        if (isEnabled() == enabled && getProfile() == targetProfile) {
            return
        }
        prefs.edit().putInt(KEY_CACHED_UPMIX_PROFILE, targetProfile).apply()

        // 1. Cap nhat AudioFlinger parameters truc tiep
        val paramVal = if (enabled) targetProfile.toString() else "0"
        audioManager.setParameters("${AudioConstants.PARAM_360RA_UPMIX_HEADPHONE}=$paramVal")
        Log.i(AudioConstants.TAG, "setParameters 360ra_upmix_headphone=$paramVal")

        // 2. Phat broadcast ACTION_SET_UPMIX_CONFIG cho jp.co.sony.threesixtyra.system
        try {
            val intent = Intent().apply {
                setPackage("jp.co.sony.threesixtyra.system")
                action = AudioConstants.ACTION_SET_UPMIX_CONFIG
                putExtra(AudioConstants.EXTRA_UPMIX_MODE, targetProfile)
                putExtra(AudioConstants.EXTRA_UPMIX_ENABLED, enabled)
            }
            context.sendBroadcastAsUser(intent, UserHandle.ALL)
            Log.i(AudioConstants.TAG, "Da phat broadcast ACTION_SET_UPMIX_CONFIG: enabled=$enabled, mode=$targetProfile")
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi phat broadcast ACTION_SET_UPMIX_CONFIG: $e")
        }
    }
}
