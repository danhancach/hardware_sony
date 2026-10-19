package com.sonyericsson.soundenhancement.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
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

// Dialog tuy chinh chi tiet Dolby Sound va cac thiet lap chuyen sau theo phong cach Material 3 Expressive
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
        val layoutCustomControls = view.findViewById<LinearLayout>(R.id.layout_dolby_custom_controls)
        val overlayDisabled = view.findViewById<View>(R.id.overlay_dolby_custom_disabled)

        // IEQ
        val chipGroupIeq = view.findViewById<ChipGroup>(R.id.cg_dolby_ieq)

        // Dialogue Enhancer
        val tvDeValue = view.findViewById<TextView>(R.id.tv_de_value)
        val sliderDe = view.findViewById<Slider>(R.id.slider_de)

        // GEQ
        val btnResetGeq = view.findViewById<MaterialButton>(R.id.btn_reset_geq)
        val dolbyGeqView = view.findViewById<DolbyGeqView>(R.id.dolby_geq_view)

        // 1. Doc profile hien tai
        val currentProfile = readCurrentProfile()
        when (currentProfile) {
            DolbyController.PROFILE_MOVIE -> radioGroup.check(R.id.rb_dolby_movie)
            DolbyController.PROFILE_MUSIC -> radioGroup.check(R.id.rb_dolby_music)
            DolbyController.PROFILE_CUSTOM -> radioGroup.check(R.id.rb_dolby_custom)
            else -> radioGroup.check(R.id.rb_dolby_dynamic)
        }

        // 2. Khoi tao trang thai cho cum dieu khien Custom
        initIeqControls(chipGroupIeq)
        initDialogueEnhancerControls(tvDeValue, sliderDe)
        initGeqControls(dolbyGeqView, btnResetGeq)

        // Cap nhat trang thai kich hoat/vo hieu hoa ban dau (khong animate)
        updateCustomControlsState(
            isCustom = (currentProfile == DolbyController.PROFILE_CUSTOM),
            layoutCustomControls = layoutCustomControls,
            overlayDisabled = overlayDisabled,
            chipGroupIeq = chipGroupIeq,
            sliderDe = sliderDe,
            dolbyGeqView = dolbyGeqView,
            btnResetGeq = btnResetGeq,
            animate = false
        )

        // Khi nguoi dung cham vao cum tuy chinh luc dang bi vo hieu hoa, tu dong chon Tuy chinh
        overlayDisabled.setOnClickListener {
            radioGroup.check(R.id.rb_dolby_custom)
        }

        // 3. Lang nghe thay doi Profile
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val profile = when (checkedId) {
                R.id.rb_dolby_movie -> DolbyController.PROFILE_MOVIE
                R.id.rb_dolby_music -> DolbyController.PROFILE_MUSIC
                R.id.rb_dolby_custom -> DolbyController.PROFILE_CUSTOM
                else -> DolbyController.PROFILE_DYNAMIC
            }
            if (dolbyController.getProfile() == profile) {
                return@setOnCheckedChangeListener
            }
            dolbyController.setProfile(profile)
            modeManager.setCustomProfile(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_DOLBY_PROFILE, profile.toString())

            // Hieu ung chuyen doi trang thai kich hoat hoac vo hieu hoa cum dieu khien Custom
            val isCustom = (profile == DolbyController.PROFILE_CUSTOM)
            updateCustomControlsState(
                isCustom = isCustom,
                layoutCustomControls = layoutCustomControls,
                overlayDisabled = overlayDisabled,
                chipGroupIeq = chipGroupIeq,
                sliderDe = sliderDe,
                dolbyGeqView = dolbyGeqView,
                btnResetGeq = btnResetGeq,
                animate = true
            )

            onProfileChanged?.invoke(profile)
        }
    }

    private fun updateCustomControlsState(
        isCustom: Boolean,
        layoutCustomControls: View,
        overlayDisabled: View,
        chipGroupIeq: ChipGroup,
        sliderDe: Slider,
        dolbyGeqView: DolbyGeqView,
        btnResetGeq: MaterialButton,
        animate: Boolean
    ) {
        overlayDisabled.visibility = if (isCustom) View.GONE else View.VISIBLE

        val targetAlpha = if (isCustom) 1.0f else 0.38f
        if (animate) {
            layoutCustomControls.animate()
                .alpha(targetAlpha)
                .setDuration(220)
                .start()
        } else {
            layoutCustomControls.alpha = targetAlpha
        }

        chipGroupIeq.isEnabled = isCustom
        for (i in 0 until chipGroupIeq.childCount) {
            chipGroupIeq.getChildAt(i).isEnabled = isCustom
        }
        sliderDe.isEnabled = isCustom
        dolbyGeqView.isEnabled = isCustom
        btnResetGeq.isEnabled = isCustom
    }

    private fun initIeqControls(chipGroup: ChipGroup) {
        val currentIeq = dolbyController.getIeqPreset()
        when (currentIeq) {
            DolbyController.IEQ_BRIGHT -> chipGroup.check(R.id.chip_ieq_bright)
            DolbyController.IEQ_BALANCED -> chipGroup.check(R.id.chip_ieq_balanced)
            DolbyController.IEQ_WARM -> chipGroup.check(R.id.chip_ieq_warm)
            else -> chipGroup.check(R.id.chip_ieq_off)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            // Luon dam bao co dung mot the duoc chon; neu uncheck thi tu dong quay ve the Tat
            if (checkedIds.isEmpty()) {
                group.check(R.id.chip_ieq_off)
                return@setOnCheckedStateChangeListener
            }
            val selectedId = checkedIds.first()
            val preset = when (selectedId) {
                R.id.chip_ieq_bright -> DolbyController.IEQ_BRIGHT
                R.id.chip_ieq_balanced -> DolbyController.IEQ_BALANCED
                R.id.chip_ieq_warm -> DolbyController.IEQ_WARM
                else -> DolbyController.IEQ_OFF
            }
            if (dolbyController.getIeqPreset() == preset) {
                return@setOnCheckedStateChangeListener
            }
            dolbyController.setIeqPreset(preset)
        }
    }

    private fun initDialogueEnhancerControls(tvValue: TextView, slider: Slider) {
        val currentAmount = dolbyController.getDialogueEnhancerAmount()
        slider.value = currentAmount.toFloat()
        updateDeText(tvValue, currentAmount)

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intVal = value.toInt()
                updateDeText(tvValue, intVal)
                dolbyController.setDialogueEnhancerAmount(intVal)
            }
        }
    }

    private fun updateDeText(tvValue: TextView, amount: Int) {
        if (amount == 0) {
            tvValue.setText(R.string.sound_enhance_dolby_de_off)
        } else {
            tvValue.text = getString(R.string.sound_enhance_dolby_de_level, amount)
        }
    }

    private fun initGeqControls(geqView: DolbyGeqView, btnReset: MaterialButton) {
        val currentGains = dolbyController.getGeqBandGains()
        geqView.setBandGains(currentGains)

        geqView.onGainsChangedListener = { newGains ->
            dolbyController.setGeqBandGains(newGains)
        }

        btnReset.setOnClickListener {
            geqView.resetGains()
            dolbyController.resetGeqBandGains()
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

    override fun onDestroy() {
        dolbyController.release()
        super.onDestroy()
    }
}
