package com.sonyericsson.soundenhancement.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.backend.AudioConstants
import com.sonyericsson.soundenhancement.backend.AudioModeManager
import com.sonyericsson.soundenhancement.backend.DolbyController

// Ap dung mau dong Material 3, edge-to-edge va chieu cao expanded cho BottomSheetDialog khi dung host SettingsLib
internal fun configureSoundBottomSheetDialog(
    resources: android.content.res.Resources,
    dialog: BottomSheetDialog,
    fillMaxHeight: Boolean = false,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dialog.context.theme.applyStyle(
            com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight,
            true,
        )
    }
    dialog.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }
    dialog.setOnShowListener {
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return@setOnShowListener
        // Gan nen phu tran xuong tan day man hinh, bao trum ca thanh dieu huong cu chi
        bottomSheet.setBackgroundResource(R.drawable.sound_dialog_bg)

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isFitToContents = true
        behavior.isShouldRemoveExpandedCorners = false
        behavior.skipCollapsed = true

        // Gioi han chieu cao toi da duoi status bar de khong de len status bar va giu nguyen bo goc 28dp
        val windowHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dialog.window?.windowManager?.currentWindowMetrics?.bounds?.height()
                ?: resources.displayMetrics.heightPixels
        } else {
            resources.displayMetrics.heightPixels
        }

        val statusBarHeight = run {
            val insetsTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog.window?.decorView?.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
            } else 0
            if (insetsTop > 0) {
                insetsTop
            } else {
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else (28 * resources.displayMetrics.density).toInt()
            }
        }

        val targetHeight = windowHeight - statusBarHeight
        behavior.maxHeight = targetHeight
        if (fillMaxHeight) {
            bottomSheet.layoutParams.height = targetHeight
            bottomSheet.requestLayout()
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}

// Dialog tuy chinh chi tiet Dolby Sound theo phong cach M3 Expressive
class DolbyDetailBottomSheet(
    private val onProfileChanged: ((Int) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private lateinit var modeManager: AudioModeManager
    private lateinit var dolbyController: DolbyController

    override fun getTheme(): Int = R.style.ThemeOverlay_SoundEnhancement_BottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        configureSoundBottomSheetDialog(resources, dialog, fillMaxHeight = true)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dolby_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        modeManager = AudioModeManager(ctx)
        dolbyController = DolbyController(ctx)

        val radioGroup = view.findViewById<RadioGroup>(R.id.rg_dolby_profile)
        val btnOpenDolby = view.findViewById<MaterialButton>(R.id.btn_open_dolby_app)

        // Doc profile hien tai tu database
        val currentProfile = readCurrentProfile()
        when (currentProfile) {
            DolbyController.PROFILE_MOVIE -> radioGroup.check(R.id.rb_dolby_movie)
            DolbyController.PROFILE_MUSIC -> radioGroup.check(R.id.rb_dolby_music)
            DolbyController.PROFILE_CUSTOM -> radioGroup.check(R.id.rb_dolby_custom)
            else -> radioGroup.check(R.id.rb_dolby_dynamic)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val profile = when (checkedId) {
                R.id.rb_dolby_movie -> DolbyController.PROFILE_MOVIE
                R.id.rb_dolby_music -> DolbyController.PROFILE_MUSIC
                R.id.rb_dolby_custom -> DolbyController.PROFILE_CUSTOM
                else -> DolbyController.PROFILE_DYNAMIC
            }
            dolbyController.setProfile(profile)
            modeManager.setCustomProfile(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_DOLBY_PROFILE, profile.toString())
            onProfileChanged?.invoke(profile)
        }

        btnOpenDolby.setOnClickListener {
            val launched = tryLaunchIntent(Intent(AudioConstants.ACTION_LAUNCH_DOLBY_APP)) ||
                    tryLaunchComponent("com.dolby.daxappui2", "com.dolby.daxappui2.MainActivity")
            if (!launched) {
                Toast.makeText(ctx, "Khong tim thay ung dung Dolby Equalizer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readCurrentProfile(): Int {
        val current = dolbyController.getProfile()
        if (current in 0..3) return current

        var profile = DolbyController.PROFILE_DYNAMIC
        var cursor = null as android.database.Cursor?
        try {
            val uri = android.net.Uri.parse(AudioConstants.URI_EXTAUDIO_INFO)
            cursor = requireContext().contentResolver.query(
                uri, null, "feature='${AudioConstants.SELECTION_CUSTOM_PROFILE}'", null, null
            )
            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex("key_name")
                val valueIndex = cursor.getColumnIndex("key_value")
                while (cursor.moveToNext()) {
                    val key = cursor.getString(nameIndex)
                    if (AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_DOLBY_PROFILE == key) {
                        profile = cursor.getString(valueIndex)?.toIntOrNull() ?: DolbyController.PROFILE_DYNAMIC
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Bo qua loi
        } finally {
            cursor?.close()
        }
        return profile
    }

    private fun tryLaunchIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun tryLaunchComponent(pkg: String, cls: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
