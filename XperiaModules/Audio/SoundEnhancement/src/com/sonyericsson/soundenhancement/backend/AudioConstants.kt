package com.sonyericsson.soundenhancement.backend

// Dinh nghia cac hang so giao tiep stack am thanh Sony
object AudioConstants {
    const val TAG = "SoundEnhancement"

    // URI cho ExtendedAudioService
    const val URI_EXTAUDIO_INFO = "content://extaudio/info"
    const val SELECTION_EFFECT_SELECTOR = "effectselector"
    const val SELECTION_CUSTOM_PROFILE = "custom-profile"

    // Key Database trong ExtendedAudioService
    const val KEY_DB_AUDIOEFFECT_MODE = "audioeffect_mode"
    const val KEY_DB_AUDIOEFFECT_CUSTOM_EFFECT = "audioeffect_custom_effect"
    const val KEY_DB_AUDIOEFFECT_CUSTOM_DOLBY_PROFILE = "audioeffect_custom_dolby_profile"
    const val KEY_DB_AUDIOEFFECT_CUSTOM_UPMIX_PROFILE = "audioeffect_custom_upmix_profile"

    // Che do phat lai (Playback modes)
    const val MODE_RECOMMENDED = "recommend"
    const val MODE_HIRES = "hires"
    const val MODE_SPATIAL = "spatial"
    const val MODE_CUSTOM = "custom"

    // Parameters cho AudioManager
    const val PARAM_DSEE_HX_STATE = "dsee_hx_state"
    const val PARAM_DOLBY_DAP_ENABLE = "dolby_dap_enable"
    const val PARAM_DOLBY_DEC_EXIST = "dolby_dec_exist"
    const val PARAM_360RA_UPMIX_HEADPHONE = "360ra_upmix_headphone"
    const val PARAM_WIND_NR_SUPPORTED = "wind_nr_supported"
    const val PARAM_WIND_NR_ENABLED = "wind_nr_enabled"

    // Whitelist / Effect Priority parameters
    const val PARAM_SUPPORTED_APPS_PRESET = "audio_playback_effect_supported_apps_preset"
    const val PARAM_SUPPORTED_APPS_EXCLUDE = "audio_playback_effect_supported_apps_exclude_preset"
    const val PARAM_SUPPORTED_APPS_USER = "audio_playback_effect_supported_apps_user_registed"

    // Broadcasts cho 360 Reality Audio
    const val ACTION_SET_UPMIX_CONFIG = "jp.co.sony.threesixtyra.system.ACTION_SET_UPMIX_CONFIG"
    const val ACTION_SET_UPMIX_ENABLED = "jp.co.sony.threesixtyra.system.ACTION_SET_UPMIX_ENABLED"
    const val ACTION_GET_UPMIX_CONFIG = "jp.co.sony.threesixtyra.system.ACTION_GET_UPMIX_CONFIG"
    const val BROADCAST_PERMISSION_360 = "jp.co.sony.threesixtyra.system.permission.ACCESS"
    const val EXTRA_UPMIX_MODE = "jp.co.sony.threesixtyra.system.EXTRA_UPMIX_MODE"
    const val EXTRA_UPMIX_ENABLED = "jp.co.sony.threesixtyra.system.EXTRA_UPMIX_ENABLED"

    // Intent goi app Dolby
    const val ACTION_LAUNCH_DOLBY_APP = "com.dolby.LAUNCH_DS_APP"
}
