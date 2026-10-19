package com.sonyericsson.soundenhancement.backend

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

// Dieu khien toan dien hieu ung Dolby Sound va cac tham so nang cao tren Sony Xperia (sm8550 / pdx237)
class DolbyController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

        // Cac Profile am thanh
        const val PROFILE_DYNAMIC = 0
        const val PROFILE_MOVIE = 1
        const val PROFILE_MUSIC = 2
        const val PROFILE_CUSTOM = 3

        // Cac che do Bo can bang thong minh (Intelligent Equalizer - IEQ)
        const val IEQ_OFF = 0
        const val IEQ_BRIGHT = 1
        const val IEQ_BALANCED = 2
        const val IEQ_WARM = 4

        // Ma tham so DAP (DsParams)
        private const val DS_PARAM_DS_ON = 0
        private const val DS_PARAM_PROFILE = 1
        private const val PARAM_IEQ_PRESET = 104
        private const val PARAM_DE_ENABLE = 105
        private const val PARAM_GEQ_ENABLE = 106
        private const val PARAM_DE_AMOUNT = 108
        private const val PARAM_GEQ_BAND_GAINS = 110

        private const val PREFS_NAME = "dolby_custom_settings"
        private const val KEY_CUSTOM_IEQ = "custom_ieq_preset"
        private const val KEY_CUSTOM_DE = "custom_de_amount"
        private const val KEY_CUSTOM_GEQ_10 = "custom_geq_10_bands"
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

    // Gui mang tham so dap theo profile cu the cho AudioEffect.setParameter(5, ...)
    private fun setDapParameter(effect: AudioEffect, profile: Int, paramId: Int, values: IntArray): Boolean {
        return try {
            val length = values.size
            val buffer = ByteBuffer.allocate((length + 4) * 4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(16777216) // 0x01000000 = (1 << 24)
            buffer.putInt(length + 1)
            buffer.putInt(profile)
            buffer.putInt(paramId)
            for (v in values) {
                buffer.putInt(v)
            }
            val result = effect.setParameter(5, buffer.array())
            result >= 0
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi setDapParameter($profile, $paramId): $e")
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
        if (isEnabled() == enabled) {
            return
        }
        val intVal = if (enabled) 1 else 0

        // 1. Cap nhat Settings.Global (DaxService SettingObserver se nhan va ap dung)
        try {
            Settings.Global.putInt(context.contentResolver, SETTING_DOLBY_STATE, intVal)
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi ghi vao Settings.Global dlb_dap_state: $e")
        }

        // 2. Gui broadcast DAP_PARAMS_UPDATE cho DaxService
        sendDapUpdateBroadcast(EVENT_DS_STATE_CHANGE, intVal)

        // 3. Tac dong AudioEffect DSP session 0
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

        // Neu bat lai va dang o Profile Custom, ap dung lai cac tham so chi tiet
        if (enabled && getProfile() == PROFILE_CUSTOM) {
            applyAllCustomSettings()
        }
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
        if (getProfile() == profile) {
            return
        }
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

        // Neu chon Custom, ap dung toan bo thiet lap do nguoi dung tinh chinh
        if (profile == PROFILE_CUSTOM) {
            applyAllCustomSettings()
        }
    }

    // ==========================================
    // CAC PHUONG THUC TUY CHINH NANG CAO (CUSTOM)
    // ==========================================

    // Doc IEQ preset hien tai
    fun getIeqPreset(): Int {
        return prefs.getInt(KEY_CUSTOM_IEQ, IEQ_OFF)
    }

    // Cai dat IEQ preset
    fun setIeqPreset(preset: Int) {
        prefs.edit().putInt(KEY_CUSTOM_IEQ, preset).apply()

        try {
            val effect = object : AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, 0, 0) {}
            try {
                setDapParameter(effect, PROFILE_CUSTOM, PARAM_IEQ_PRESET, intArrayOf(preset))
            } finally {
                effect.release()
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi setIeqPreset qua AudioEffect: $e")
        }

        audioManager.setParameters("dle_ieq_preset=$preset")
    }

    // Doc muc do Dialogue Enhancer (0 den 6)
    fun getDialogueEnhancerAmount(): Int {
        return prefs.getInt(KEY_CUSTOM_DE, 0)
    }

    // Cai dat Dialogue Enhancer (0 = tat, 1..6 = muc do)
    fun setDialogueEnhancerAmount(amount: Int) {
        val clamped = amount.coerceIn(0, 6)
        prefs.edit().putInt(KEY_CUSTOM_DE, clamped).apply()

        val isEnabled = clamped > 0
        try {
            val effect = object : AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, 0, 0) {}
            try {
                setDapParameter(effect, PROFILE_CUSTOM, PARAM_DE_ENABLE, intArrayOf(if (isEnabled) 1 else 0))
                setDapParameter(effect, PROFILE_CUSTOM, PARAM_DE_AMOUNT, intArrayOf(clamped))
            } finally {
                effect.release()
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi setDialogueEnhancerAmount qua AudioEffect: $e")
        }

        audioManager.setParameters("dle_den_amount=$clamped")
    }

    // Doc mang do loi 10 dai tan GEQ (dB tu -5.0 den +5.0)
    fun getGeqBandGains(): FloatArray {
        val savedStr = prefs.getString(KEY_CUSTOM_GEQ_10, null)
        val result = FloatArray(10) { 0.0f }
        if (!savedStr.isNullOrEmpty()) {
            val parts = savedStr.split(",")
            for (i in 0 until minOf(parts.size, result.size)) {
                result[i] = parts[i].toFloatOrNull() ?: 0.0f
            }
        }
        return result
    }

    // Cai dat mang do loi 10 dai tan va tinh toan mang 20 diem noi suy chuan Dolby
    fun setGeqBandGains(gains: FloatArray) {
        val size = minOf(gains.size, 10)
        val clamped10 = FloatArray(10) { 0.0f }
        for (i in 0 until size) {
            clamped10[i] = gains[i].coerceIn(-5.0f, 5.0f)
        }

        // Luu lai vao SharedPreferences duoi dang chuoi CSV
        val strBuilder = StringBuilder()
        for (i in clamped10.indices) {
            if (i > 0) strBuilder.append(",")
            strBuilder.append(clamped10[i])
        }
        prefs.edit().putString(KEY_CUSTOM_GEQ_10, strBuilder.toString()).apply()

        // Tinh toan 20 band theo thuat toan noi suy chuan Dolby DAP
        val dap20 = calculateDapGeq20(clamped10)

        try {
            val effect = object : AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, 0, 0) {}
            try {
                setDapParameter(effect, PROFILE_CUSTOM, PARAM_GEQ_ENABLE, intArrayOf(1))
                setDapParameter(effect, PROFILE_CUSTOM, PARAM_GEQ_BAND_GAINS, dap20)
            } finally {
                effect.release()
            }
        } catch (e: Exception) {
            Log.w(AudioConstants.TAG, "Loi khi setGeqBandGains qua AudioEffect: $e")
        }

        // Dong bo voi HAL Sony bang chuoi JSON array
        val jsonArray = JSONArray()
        for (v in dap20) {
            jsonArray.put(v)
        }
        audioManager.setParameters("dle_geq_gains=${jsonArray}")
    }

    // Dat lai bo can bang do hoa ve 0dB
    fun resetGeqBandGains() {
        setGeqBandGains(FloatArray(10) { 0.0f })
    }

    // Thuat toan tinh toan mang 20 diem noi suy tu 10 dai tan (khop 100% logic cua Dolby GeqView)
    private fun calculateDapGeq20(bands10: FloatArray): IntArray {
        val result = IntArray(20)
        val f = 16.0f

        // Dai tan dau tien (Band 0)
        result[0] = (bands10[0] * f).toInt()
        result[1] = (((bands10[0] + bands10[1]) / 2.0f) * f).toInt()

        // Cac dai tan giua (Band 1..8)
        for (i in 1..8) {
            result[i * 2 - 1] = (((bands10[i - 1] + bands10[i]) / 2.0f) * f).toInt()
            result[i * 2] = (bands10[i] * f).toInt()
            result[i * 2 + 1] = (((bands10[i] + bands10[i + 1]) / 2.0f) * f).toInt()
        }

        // Dai tan cuoi cung (Band 9)
        result[17] = (((bands10[8] + bands10[9]) / 2.0f) * f).toInt()
        result[18] = (bands10[9] * f).toInt()
        result[19] = (bands10[9] * f).toInt()

        return result
    }

    // Ap dung dong thoi tat ca cac thiet lap Custom xuong Audio DSP va HAL
    fun applyAllCustomSettings() {
        setIeqPreset(getIeqPreset())
        setDialogueEnhancerAmount(getDialogueEnhancerAmount())
        setGeqBandGains(getGeqBandGains())
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
