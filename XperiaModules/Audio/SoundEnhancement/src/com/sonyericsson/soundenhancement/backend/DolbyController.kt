package com.sonyericsson.soundenhancement.backend

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

// Dieu khien hieu ung Dolby Sound / Atmos tren Sony Xperia (sm8550 / pdx237)
class DolbyController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        val EFFECT_TYPE_DAP: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4f4a7e4fb08d")

        // Ten su kien va broadcast cua Dolby DaxService
        const val ACTION_DAP_PARAMS_UPDATE = "com.dolby.intent.action.DAP_PARAMS_UPDATE"
        const val EVENT_DS_STATE_CHANGE = "ds_state_change"
        const val EVENT_PROFILE_CHANGE = "profile_change"
        const val EXTRA_EVENT_NAME = "event name"
        const val EXTRA_INTEGER_VALUE = "Integer Value"
        const val PERMISSION_DAXSERVICE = "com.dolby.permission.DAXSERVICE"

        // Settings.Global keys cua DaxService
        const val SETTING_DOLBY_STATE = "dlb_dap_state"
        const val SETTING_DOLBY_PROFILE = "dlb_dap_profile"

        const val PROFILE_DYNAMIC = 0
        const val PROFILE_MOVIE = 1
        const val PROFILE_MUSIC = 2
        const val PROFILE_CUSTOM = 3

        private const val DS_PARAM_DS_ON = 0
        private const val DS_PARAM_PROFILE = 1
    }

    // Chuyen doi tham so bool sang byte array cho AudioEffect.setParameter(5, ...)
    private fun setBoolParam(effect: AudioEffect, paramId: Int, value: Boolean): Boolean {
        return try {
            val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(paramId)
            buffer.putInt(1)
            buffer.putInt(if (value) 1 else 0)
            val result = effect.setParameter(5, buffer.array())
            result >= 0
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi setBoolParam($paramId, $value): $e")
            false
        }
    }

    // Chuyen doi tham so int sang byte array cho AudioEffect.setParameter(5, ...)
    private fun setIntParam(effect: AudioEffect, paramId: Int, value: Int): Boolean {
        return try {
            val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(paramId)
            buffer.putInt(1)
            buffer.putInt(value)
            val result = effect.setParameter(5, buffer.array())
            result >= 0
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi setIntParam($paramId, $value): $e")
            false
        }
    }

    // Kiem tra trang thai bat/tat cua Dolby
    fun isEnabled(): Boolean {
        return try {
            val state = Settings.Global.getInt(context.contentResolver, SETTING_DOLBY_STATE, -1)
            state == 1
        } catch (e: Exception) {
            false
        }
    }

    // Bat hoac tat Dolby dong bo toan dien qua Settings, Broadcast, AudioEffect va HAL
    fun setEnabled(enabled: Boolean) {
        val intVal = if (enabled) 1 else 0

        // 1. Cap nhat Settings.Global (DaxService SettingObserver se nhan va ap dung)
        try {
            Settings.Global.putInt(context.contentResolver, SETTING_DOLBY_STATE, intVal)
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi ghi vao Settings.Global dlb_dap_state: $e")
        }

        // 2. Gui broadcast DAP_PARAMS_UPDATE cho DaxService va DaxUI
        sendDapUpdateBroadcast(EVENT_DS_STATE_CHANGE, intVal)

        // 3. Tac dong AudioEffect DSP session 0 va giai phong ngay de khong chiem control cua DaxUI
        try {
            val effect = object : AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, 0, 0) {}
            try {
                setBoolParam(effect, DS_PARAM_DS_ON, enabled)
                effect.enabled = enabled
            } finally {
                effect.release()
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi bat/tat AudioEffect truc tiep: $e")
        }

        // 4. Dong bo tham so qua AudioManager parameters
        val stateStr = intVal.toString()
        audioManager.setParameters("${AudioConstants.PARAM_DOLBY_DAP_ENABLE}=$stateStr")
        audioManager.setParameters("${AudioConstants.PARAM_DOLBY_DEC_EXIST}=$stateStr")
        audioManager.setParameters("dle_ds_state=$enabled")
    }

    // Lay profile hien tai
    fun getProfile(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, SETTING_DOLBY_PROFILE, PROFILE_DYNAMIC)
        } catch (e: Exception) {
            PROFILE_DYNAMIC
        }
    }

    // Chuyen doi profile Dolby
    fun setProfile(profile: Int) {
        try {
            Settings.Global.putInt(context.contentResolver, SETTING_DOLBY_PROFILE, profile)
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi ghi Settings.Global dlb_dap_profile: $e")
        }

        sendDapUpdateBroadcast(EVENT_PROFILE_CHANGE, profile)

        try {
            val effect = object : AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, 0, 0) {}
            try {
                setIntParam(effect, DS_PARAM_PROFILE, profile)
            } finally {
                effect.release()
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi dat int param profile: $e")
        }

        audioManager.setParameters("dolby_profile=$profile")
        audioManager.setParameters("dle_profile=$profile")
    }

    private fun sendDapUpdateBroadcast(eventName: String, value: Int) {
        val intent = Intent(ACTION_DAP_PARAMS_UPDATE).apply {
            putExtra(EXTRA_EVENT_NAME, eventName)
            putExtra(EXTRA_INTEGER_VALUE, value)
            addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY or Intent.FLAG_RECEIVER_FOREGROUND)
        }
        try {
            context.sendBroadcastAsUser(intent, UserHandle.CURRENT, PERMISSION_DAXSERVICE)
        } catch (e: Exception) {
            try {
                context.sendBroadcast(intent)
            } catch (e2: Exception) {
                Log.w(AudioConstants.TAG, "Khong the gui broadcast DAP_PARAMS_UPDATE: $e2")
            }
        }
    }

    fun release() {
        // Khong giu AudioEffect instance lau dai nen khong can release gi them
    }
}

