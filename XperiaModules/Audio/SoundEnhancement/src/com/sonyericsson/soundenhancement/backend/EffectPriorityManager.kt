package com.sonyericsson.soundenhancement.backend

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.util.Log

data class PriorityAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isPreset: Boolean,
    var isEnabled: Boolean
)

// Quan ly danh sach ung dung duoc uu tien hieu ung am thanh
class EffectPriorityManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val packageManager: PackageManager = context.packageManager

    // Danh sach 33 app mac dinh tu audio_app_white_list.xml
    val presetApps = listOf(
        "com.spotify.music",
        "com.google.android.music",
        "com.google.android.apps.youtube.music",
        "com.tencent.qqmusic",
        "com.amazon.mp3",
        "deezer.android.app",
        "jp.linecorp.linemusic.android",
        "com.apple.android.music",
        "fm.awa.liverpool",
        "com.nttdocomo.android.dhits",
        "jp.co.labelgate.moratouch",
        "com.aspiro.tidal",
        "com.pandora.android",
        "com.tencent.ibg.joox",
        "mbinc12.mb32",
        "com.netease.cloudmusic",
        "com.kddi.android.UtaPass",
        "com.sonyericsson.music",
        "jp.co.sonymusic.moraqualitasmobile",
        "com.soundcloud.android",
        "com.now.moov",
        "com.skysoft.kkbox.android",
        "com.omusic.gPhone",
        "com.ezpeer.ezpeerplus.v4",
        "com.kugou.android",
        "cn.kuwo.player",
        "com.ting.mp3.android",
        "fm.xiami.main",
        "ru.yandex.music",
        "com.uma.musicvk",
        "com.zvooq.openplay",
        "ru.mts.music.android",
        "com.sony.songpal"
    )

    // Doc danh sach app loai tru khoi preset
    fun getExcludedPresets(): MutableSet<String> {
        val param = audioManager.getParameters(AudioConstants.PARAM_SUPPORTED_APPS_EXCLUDE) ?: ""
        val result = mutableSetOf<String>()
        if (param.contains("=")) {
            val list = param.substringAfter("=").split(",")
            result.addAll(list.filter { it.isNotBlank() })
        }
        return result
    }

    // Doc danh sach app do nguoi dung them
    fun getUserRegisteredApps(): MutableSet<String> {
        val param = audioManager.getParameters(AudioConstants.PARAM_SUPPORTED_APPS_USER) ?: ""
        val result = mutableSetOf<String>()
        if (param.contains("=")) {
            val list = param.substringAfter("=").split(",")
            result.addAll(list.filter { it.isNotBlank() })
        }
        return result
    }

    // Lay toan bo danh sach cac app de hien thi tren UI
    fun loadAllPriorityApps(): List<PriorityAppItem> {
        val excluded = getExcludedPresets()
        val userApps = getUserRegisteredApps()
        val appItems = mutableListOf<PriorityAppItem>()

        // 1. Cac ung dung trong danh sach preset co tren thiet bi
        for (pkg in presetApps) {
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                val isEnabled = !excluded.contains(pkg)
                appItems.add(PriorityAppItem(pkg, label, icon, isPreset = true, isEnabled = isEnabled))
            } catch (e: PackageManager.NameNotFoundException) {
                // App chua duoc cai, bo qua
            }
        }

        // 2. Cac ung dung he thong va ung dung do nguoi dung cai (audio/media)
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in installedApps) {
            val pkg = appInfo.packageName
            if (presetApps.contains(pkg)) continue
            // Chi lay ung dung co the phat nhac hoac do user them
            val isUserRegistered = userApps.contains(pkg)
            if (isUserRegistered || (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0)) {
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }
                appItems.add(
                    PriorityAppItem(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        isPreset = false,
                        isEnabled = isUserRegistered
                    )
                )
            }
        }

        return appItems.sortedWith(compareBy({ !it.isEnabled }, { it.appName }))
    }

    // Bat hoac tat uu tien cho mot ung dung
    fun toggleAppPriority(pkg: String, enable: Boolean) {
        val isPreset = presetApps.contains(pkg)
        if (isPreset) {
            val excluded = getExcludedPresets()
            if (enable) {
                excluded.remove(pkg)
            } else {
                excluded.add(pkg)
            }
            audioManager.setParameters("${AudioConstants.PARAM_SUPPORTED_APPS_EXCLUDE}=${excluded.joinToString(",")}")
        } else {
            val userApps = getUserRegisteredApps()
            if (enable) {
                userApps.add(pkg)
            } else {
                userApps.remove(pkg)
            }
            audioManager.setParameters("${AudioConstants.PARAM_SUPPORTED_APPS_USER}=${userApps.joinToString(",")}")
        }
    }

    // Dem so luong app dang duoc bat uu tien
    fun getActivePriorityAppCount(): Int {
        val excluded = getExcludedPresets()
        val userApps = getUserRegisteredApps()
        var count = 0
        for (pkg in presetApps) {
            try {
                packageManager.getApplicationInfo(pkg, 0)
                if (!excluded.contains(pkg)) count++
            } catch (e: Exception) {
                // Bo qua
            }
        }
        count += userApps.size
        return count
    }
}
