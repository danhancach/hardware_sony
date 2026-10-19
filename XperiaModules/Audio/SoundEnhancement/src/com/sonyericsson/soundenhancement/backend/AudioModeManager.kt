package com.sonyericsson.soundenhancement.backend

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log

// Quan ly che do am thanh qua ExtendedAudioService va SharedPreferences cache
class AudioModeManager(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver
    private val prefs: SharedPreferences = context.getSharedPreferences("sound_enhancement_prefs", Context.MODE_PRIVATE)

    // Doc che do hien tai tu ExtendedAudioService hoac SharedPreferences
    fun getAudioEffectMode(): String {
        var mode: String? = null
        var cursor = null as android.database.Cursor?
        try {
            val uri = Uri.parse(AudioConstants.URI_EXTAUDIO_INFO)
            cursor = contentResolver.query(uri, null, "feature='${AudioConstants.SELECTION_EFFECT_SELECTOR}'", null, null)
            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex("key_name")
                val valueIndex = cursor.getColumnIndex("key_value")
                while (cursor.moveToNext()) {
                    val key = cursor.getString(nameIndex)
                    if (AudioConstants.KEY_DB_AUDIOEFFECT_MODE == key) {
                        mode = cursor.getString(valueIndex)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(AudioConstants.TAG, "Loi khi doc audioeffect_mode tu db: $e")
        } finally {
            cursor?.close()
        }

        // Neu db chua co thi fallback ve SharedPreferences hoac mac dinh MODE_RECOMMENDED
        val finalMode = mode ?: prefs.getString(AudioConstants.KEY_DB_AUDIOEFFECT_MODE, AudioConstants.MODE_RECOMMENDED) ?: AudioConstants.MODE_RECOMMENDED
        return finalMode
    }

    // Cap nhat che do am thanh va dong bo effectselector
    fun setAudioEffectMode(selectedMode: String) {
        // Luu vao SharedPreferences truoc de UI luon nhat quan
        prefs.edit().putString(AudioConstants.KEY_DB_AUDIOEFFECT_MODE, selectedMode).apply()

        var rowId = -1L
        var cursor = null as android.database.Cursor?
        val uri = Uri.parse(AudioConstants.URI_EXTAUDIO_INFO)
        try {
            cursor = contentResolver.query(uri, null, "feature='${AudioConstants.SELECTION_EFFECT_SELECTOR}'", null, null)
            if (cursor != null) {
                val idIndex = cursor.getColumnIndex("_id")
                val nameIndex = cursor.getColumnIndex("key_name")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val key = cursor.getString(nameIndex)
                    if (AudioConstants.KEY_DB_AUDIOEFFECT_MODE == key) {
                        rowId = id
                        break
                    }
                }
            }
            if (rowId >= 0) {
                val rowUri = Uri.parse("${AudioConstants.URI_EXTAUDIO_INFO}/$rowId")
                val values = ContentValues().apply {
                    put("key_value", selectedMode)
                }
                contentResolver.update(rowUri, values, null)
            } else {
                // Chen hang moi neu chua ton tai
                val values = ContentValues().apply {
                    put("feature", AudioConstants.SELECTION_EFFECT_SELECTOR)
                    put("key_name", AudioConstants.KEY_DB_AUDIOEFFECT_MODE)
                    put("key_value", selectedMode)
                    put("version", 1)
                    put("hal_sync", 1)
                }
                contentResolver.insert(uri, values)
            }
            contentResolver.call(uri, "sync", AudioConstants.SELECTION_EFFECT_SELECTOR, null as Bundle?)
        } catch (e: Exception) {
            Log.e(AudioConstants.TAG, "Loi khi cap nhat audioeffect_mode: $e")
        } finally {
            cursor?.close()
        }
    }

    // Cap nhat danh sach custom effect (dolby, upmix, dsee)
    fun setCustomEffect(dolby: Boolean, upmix: Boolean, dsee: Boolean) {
        val state = when {
            dolby && upmix -> "dolby,upmix"
            dolby && dsee -> "dolby,dsee"
            dolby -> "dolby"
            upmix -> "upmix"
            dsee -> "dsee"
            else -> "off"
        }
        prefs.edit().putString(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_EFFECT, state).apply()

        var rowId = -1L
        var cursor = null as android.database.Cursor?
        val uri = Uri.parse(AudioConstants.URI_EXTAUDIO_INFO)
        try {
            cursor = contentResolver.query(uri, null, "feature='${AudioConstants.SELECTION_EFFECT_SELECTOR}'", null, null)
            if (cursor != null) {
                val idIndex = cursor.getColumnIndex("_id")
                val nameIndex = cursor.getColumnIndex("key_name")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val key = cursor.getString(nameIndex)
                    if (AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_EFFECT == key) {
                        rowId = id
                        break
                    }
                }
            }
            if (rowId >= 0) {
                val rowUri = Uri.parse("${AudioConstants.URI_EXTAUDIO_INFO}/$rowId")
                val values = ContentValues().apply {
                    put("key_value", state)
                }
                contentResolver.update(rowUri, values, null)
            } else {
                val values = ContentValues().apply {
                    put("feature", AudioConstants.SELECTION_EFFECT_SELECTOR)
                    put("key_name", AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_EFFECT)
                    put("key_value", state)
                    put("version", 1)
                    put("hal_sync", 1)
                }
                contentResolver.insert(uri, values)
            }
            contentResolver.call(uri, "sync", AudioConstants.SELECTION_EFFECT_SELECTOR, null as Bundle?)
        } catch (e: Exception) {
            Log.e(AudioConstants.TAG, "Loi khi cap nhat custom effect: $e")
        } finally {
            cursor?.close()
        }
    }

    // Cap nhat profile custom (dolby profile, upmix profile)
    fun setCustomProfile(keyName: String, profileValue: String) {
        prefs.edit().putString(keyName, profileValue).apply()

        var rowId = -1L
        var cursor = null as android.database.Cursor?
        val uri = Uri.parse(AudioConstants.URI_EXTAUDIO_INFO)
        try {
            cursor = contentResolver.query(uri, null, "feature='${AudioConstants.SELECTION_CUSTOM_PROFILE}'", null, null)
            if (cursor != null) {
                val idIndex = cursor.getColumnIndex("_id")
                val nameIndex = cursor.getColumnIndex("key_name")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val key = cursor.getString(nameIndex)
                    if (keyName == key) {
                        rowId = id
                        break
                    }
                }
            }
            if (rowId >= 0) {
                val rowUri = Uri.parse("${AudioConstants.URI_EXTAUDIO_INFO}/$rowId")
                val values = ContentValues().apply {
                    put("key_value", profileValue)
                }
                contentResolver.update(rowUri, values, null)
            } else {
                val values = ContentValues().apply {
                    put("feature", AudioConstants.SELECTION_CUSTOM_PROFILE)
                    put("key_name", keyName)
                    put("key_value", profileValue)
                    put("version", 1)
                    put("hal_sync", 1)
                }
                contentResolver.insert(uri, values)
            }
            contentResolver.call(uri, "sync", AudioConstants.SELECTION_CUSTOM_PROFILE, null as Bundle?)
        } catch (e: Exception) {
            Log.e(AudioConstants.TAG, "Loi khi cap nhat custom profile: $e")
        } finally {
            cursor?.close()
        }
    }

    fun getCustomProfile(keyName: String, defaultVal: String): String {
        return prefs.getString(keyName, defaultVal) ?: defaultVal
    }
}
