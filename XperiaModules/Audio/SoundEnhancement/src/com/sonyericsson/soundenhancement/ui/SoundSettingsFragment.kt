package com.sonyericsson.soundenhancement.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.backend.AudioConstants
import com.sonyericsson.soundenhancement.backend.AudioModeManager
import com.sonyericsson.soundenhancement.backend.DolbyController
import com.sonyericsson.soundenhancement.backend.DseeController
import com.sonyericsson.soundenhancement.backend.EffectPriorityManager
import com.sonyericsson.soundenhancement.backend.SpatialAudioController
import com.sonyericsson.soundenhancement.backend.WindNoiseController

// Fragment chinh dung PreferenceScreen/RecyclerView chung duong cuon voi toan bo menu Xperia Settings
class SoundSettingsFragment : SettingsBasePreferenceFragment() {

    // Card tu quan ly khoang cach doc trong XML, tat item decoration mac dinh de tranh doi le
    override val isPreferenceSpacingEnabled: Boolean = false

    private lateinit var modeManager: AudioModeManager
    private lateinit var dolbyController: DolbyController
    private lateinit var dseeController: DseeController
    private lateinit var spatialController: SpatialAudioController
    private lateinit var windController: WindNoiseController
    private lateinit var priorityManager: EffectPriorityManager

    // Views Hero Card
    private lateinit var imgIllustration: ImageView
    private lateinit var tvModeTitle: TextView
    private lateinit var tvModeSummary: TextView
    private lateinit var badgeModeStatus: TextView
    private lateinit var chipGroupModes: ChipGroup
    private lateinit var chipRecommended: Chip
    private lateinit var chipCustom: Chip

    // Views Feature Cards
    private lateinit var btnDolbyDetail: View
    private lateinit var btnDolbyToggleArea: View
    private lateinit var switchDolby: MaterialSwitch
    private lateinit var tvDolbySummary: TextView

    private lateinit var btnDseeDetail: View
    private lateinit var btnDseeToggleArea: View
    private lateinit var switchDsee: MaterialSwitch

    private lateinit var btnThreesixtyDetail: View
    private lateinit var btnThreesixtyToggleArea: View
    private lateinit var switchThreesixty: MaterialSwitch
    private lateinit var tvThreesixtySummary: TextView

    private lateinit var rowEffectPriority: View
    private lateinit var tvPriorityCount: TextView

    // Recording Preference & Views
    private var recordingPreference: SoundLayoutPreference? = null
    private lateinit var rowWindNr: View
    private lateinit var switchWindNr: MaterialSwitch

    private var isUpdatingUi = false
    private var observersRegistered = false

    // Observer theo doi thay doi trang thai Dolby tu DaxService / DaxUI
    private val dolbySettingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (isAdded && !isUpdatingUi) {
                updateUiState()
            }
        }
    }

    private val dolbyIntentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isAdded && !isUpdatingUi) {
                updateUiState()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sound_settings, rootKey)

        val ctx = requireContext()
        modeManager = AudioModeManager(ctx)
        dolbyController = DolbyController(ctx)
        dseeController = DseeController(ctx)
        spatialController = SpatialAudioController(ctx)
        windController = WindNoiseController(ctx)
        priorityManager = EffectPriorityManager(ctx)

        initPreferenceViews()
        initCustomSettingsIfFirstTime()
        setupListeners()
        registerDolbyObserversIfNeeded()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView?.let { recycler ->
            recycler.clipToPadding = false
            val extraBottomPx = (40 * resources.displayMetrics.density).toInt()
            recycler.setPadding(
                recycler.paddingLeft,
                recycler.paddingTop,
                recycler.paddingRight,
                extraBottomPx,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sound_enhance_audio_settings_title)
        updateUiState()
    }

    private fun registerDolbyObserversIfNeeded() {
        if (observersRegistered) return
        val ctx = requireContext()
        val resolver = ctx.contentResolver
        try {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(DolbyController.SETTING_DOLBY_STATE),
                false,
                dolbySettingsObserver,
            )
            resolver.registerContentObserver(
                Settings.Global.getUriFor(DolbyController.SETTING_DOLBY_PROFILE),
                false,
                dolbySettingsObserver,
            )
            val filter = IntentFilter(DolbyController.ACTION_DAP_PARAMS_UPDATE)
            ctx.registerReceiver(dolbyIntentReceiver, filter, Context.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            android.util.Log.w(AudioConstants.TAG, "Khong the dang ky dolby observers: $e")
        }
        observersRegistered = true
    }

    private fun initPreferenceViews() {
        val heroPref = findPreference<SoundLayoutPreference>(KEY_HERO_MODE)!!
        imgIllustration = heroPref.findViewById(R.id.img_mode_illustration)
        tvModeTitle = heroPref.findViewById(R.id.tv_current_mode_title)
        tvModeSummary = heroPref.findViewById(R.id.tv_current_mode_summary)
        badgeModeStatus = heroPref.findViewById(R.id.badge_mode_status)
        chipGroupModes = heroPref.findViewById(R.id.chip_group_modes)
        chipRecommended = heroPref.findViewById(R.id.chip_mode_recommended)
        chipCustom = heroPref.findViewById(R.id.chip_mode_custom)

        val dolbyPref = findPreference<SoundLayoutPreference>(KEY_DOLBY)!!
        btnDolbyDetail = dolbyPref.findViewById(R.id.btn_dolby_detail)
        btnDolbyToggleArea = dolbyPref.findViewById(R.id.btn_dolby_toggle_area)
        switchDolby = dolbyPref.findViewById(R.id.switch_dolby)
        tvDolbySummary = dolbyPref.findViewById(R.id.tv_dolby_summary)

        val dseePref = findPreference<SoundLayoutPreference>(KEY_DSEE)!!
        btnDseeDetail = dseePref.findViewById(R.id.btn_dsee_detail)
        btnDseeToggleArea = dseePref.findViewById(R.id.btn_dsee_toggle_area)
        switchDsee = dseePref.findViewById(R.id.switch_dsee)

        val threesixtyPref = findPreference<SoundLayoutPreference>(KEY_THREESIXTY)!!
        btnThreesixtyDetail = threesixtyPref.findViewById(R.id.btn_threesixty_detail)
        btnThreesixtyToggleArea = threesixtyPref.findViewById(R.id.btn_threesixty_toggle_area)
        switchThreesixty = threesixtyPref.findViewById(R.id.switch_threesixty)
        tvThreesixtySummary = threesixtyPref.findViewById(R.id.tv_threesixty_summary)

        val effectPriorityPref = findPreference<SoundLayoutPreference>(KEY_EFFECT_PRIORITY)!!
        rowEffectPriority = effectPriorityPref.findViewById(R.id.row_effect_priority)
        tvPriorityCount = effectPriorityPref.findViewById(R.id.tv_priority_count)

        recordingPreference = findPreference(KEY_RECORDING)
        rowWindNr = recordingPreference!!.findViewById(R.id.row_wind_nr)
        switchWindNr = recordingPreference!!.findViewById(R.id.switch_wind_nr)
    }

    private fun setupListeners() {
        // Rut gon con 2 che do: De xuat va Tuy chinh (single-select)
        chipRecommended.setOnClickListener {
            if (!isUpdatingUi) applyMode(AudioConstants.MODE_RECOMMENDED)
        }
        chipCustom.setOnClickListener {
            if (!isUpdatingUi) applyMode(AudioConstants.MODE_CUSTOM)
        }

        chipGroupModes.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isUpdatingUi) return@setOnCheckedStateChangeListener
            when (checkedIds.firstOrNull()) {
                R.id.chip_mode_recommended -> applyMode(AudioConstants.MODE_RECOMMENDED)
                R.id.chip_mode_custom -> applyMode(AudioConstants.MODE_CUSTOM)
            }
        }

        // Hang Dolby Sound: Vung chi tiet ben trai -> mo BottomSheet
        btnDolbyDetail.setOnClickListener {
            if (isCurrentModeCustom()) {
                showDolbyDetailBottomSheet()
            } else {
                showManagedByRecommendToast()
            }
        }

        // Hang Dolby Sound: Vung cong tac ben phai -> chi bat/tat
        btnDolbyToggleArea.setOnClickListener {
            if (isCurrentModeCustom()) {
                val newState = !switchDolby.isChecked
                switchDolby.isChecked = newState
                dolbyController.setEnabled(newState)
                saveCurrentAsCustomSettings()
                updateUiState()
            } else {
                showManagedByRecommendToast()
            }
        }

        // Hang DSEE Ultimate
        val dseeClickListener = View.OnClickListener {
            if (isCurrentModeCustom()) {
                val newState = !switchDsee.isChecked
                switchDsee.isChecked = newState
                dseeController.setEnabled(newState)
                // Logic loai tru: bat DSEE thi phai tat 360 Upmix giong behavior stock
                if (newState && switchThreesixty.isChecked) {
                    switchThreesixty.isChecked = false
                    spatialController.setEnabled(false)
                }
                saveCurrentAsCustomSettings()
                updateUiState()
            } else {
                showManagedByRecommendToast()
            }
        }
        btnDseeDetail.setOnClickListener(dseeClickListener)
        btnDseeToggleArea.setOnClickListener(dseeClickListener)

        // Hang 360 Upmix: Vung chi tiet ben trai -> mo BottomSheet
        btnThreesixtyDetail.setOnClickListener {
            if (isCurrentModeCustom()) {
                showSpatialDetailBottomSheet()
            } else {
                showManagedByRecommendToast()
            }
        }

        // Hang 360 Upmix: Vung cong tac ben phai -> chi bat/tat
        btnThreesixtyToggleArea.setOnClickListener {
            if (isCurrentModeCustom()) {
                val newState = !switchThreesixty.isChecked
                switchThreesixty.isChecked = newState
                spatialController.setEnabled(newState)
                // Logic loai tru: bat 360 Upmix thi phai tat DSEE giong behavior stock
                if (newState && switchDsee.isChecked) {
                    switchDsee.isChecked = false
                    dseeController.setEnabled(false)
                }
                saveCurrentAsCustomSettings()
                updateUiState()
            } else {
                showManagedByRecommendToast()
            }
        }

        // Hang Uu tien hieu ung (Effect Priority)
        rowEffectPriority.setOnClickListener {
            val ctx = requireContext()
            ctx.startActivity(Intent(ctx, EffectPriorityActivity::class.java))
        }

        // Hang Giam tieng on cua gio (Wind Noise Reduction)
        rowWindNr.setOnClickListener {
            val newState = !switchWindNr.isChecked
            switchWindNr.isChecked = newState
            windController.setEnabled(newState)
        }
    }

    private fun isCurrentModeCustom(): Boolean {
        return modeManager.getAudioEffectMode() == AudioConstants.MODE_CUSTOM
    }

    private fun showManagedByRecommendToast() {
        Toast.makeText(
            requireContext(),
            R.string.sound_enhance_managed_by_recommend,
            Toast.LENGTH_SHORT,
        ).show()
    }

    // Khoi tao gia tri tuy chinh ban dau neu chua tung luu
    private fun initCustomSettingsIfFirstTime() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("custom_settings_initialized")) {
            prefs.edit()
                .putBoolean("custom_settings_initialized", true)
                .putBoolean("custom_saved_dolby", dolbyController.isEnabled())
                .putBoolean("custom_saved_dsee", dseeController.isEnabled())
                .putBoolean("custom_saved_spatial", spatialController.isEnabled())
                .putInt("custom_saved_spatial_profile", spatialController.getProfile())
                .putInt("custom_saved_dolby_profile", dolbyController.getProfile())
                .apply()
        }
    }

    // Luu lai toan bo cai dat tuy chinh hien tai vao SharedPreferences va database
    private fun saveCurrentAsCustomSettings() {
        if (!isCurrentModeCustom()) return
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("custom_saved_dolby", switchDolby.isChecked)
            .putBoolean("custom_saved_dsee", switchDsee.isChecked)
            .putBoolean("custom_saved_spatial", switchThreesixty.isChecked)
            .putInt("custom_saved_spatial_profile", spatialController.getProfile())
            .putInt("custom_saved_dolby_profile", dolbyController.getProfile())
            .apply()

        modeManager.setCustomEffect(
            dolby = switchDolby.isChecked,
            upmix = switchThreesixty.isChecked,
            dsee = switchDsee.isChecked,
        )
        updateHeroIllustration(AudioConstants.MODE_CUSTOM)
    }

    // Khoi phuc chinh xac cac cai dat tuy chinh ma nguoi dung da thiet lap truoc do
    private fun restoreCustomSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDolby = prefs.getBoolean("custom_saved_dolby", true)
        val savedDolbyProfile = prefs.getInt(
            "custom_saved_dolby_profile",
            DolbyController.PROFILE_DYNAMIC,
        )
        val savedDsee = prefs.getBoolean("custom_saved_dsee", false)
        val savedSpatial = prefs.getBoolean("custom_saved_spatial", true)
        val savedSpatialProfile = prefs.getInt(
            "custom_saved_spatial_profile",
            SpatialAudioController.UPMIX_PROFILE_AUTO,
        )

        // 1. Khoi phuc Dolby
        dolbyController.setEnabled(savedDolby)
        dolbyController.setProfile(savedDolbyProfile)

        // 2. Khoi phuc DSEE va 360 Upmix theo quy tac loai tru
        if (savedSpatial) {
            spatialController.setEnabled(true, savedSpatialProfile)
            dseeController.setEnabled(false)
        } else if (savedDsee) {
            spatialController.setEnabled(false)
            dseeController.setEnabled(true)
        } else {
            spatialController.setEnabled(false)
            dseeController.setEnabled(false)
        }

        // 3. Dong bo lai DB custom effect
        modeManager.setCustomEffect(
            dolby = savedDolby,
            upmix = savedSpatial,
            dsee = if (savedSpatial) false else savedDsee,
        )
    }

    private fun applyMode(mode: String) {
        if (mode == AudioConstants.MODE_CUSTOM) {
            modeManager.setAudioEffectMode(mode)
            restoreCustomSettings()
        } else if (mode == AudioConstants.MODE_RECOMMENDED) {
            // Luu lai cai dat tuy chinh truoc khi he thong de xuat ghi de
            saveCurrentAsCustomSettings()
            modeManager.setAudioEffectMode(mode)
            // Khi o che do De xuat, he thong mac dinh toi uu hoa tu dong (Dolby + DSEE, tat 360)
            dolbyController.setEnabled(true)
            dseeController.setEnabled(true)
            spatialController.setEnabled(false)
        }
        updateUiState()
    }

    private fun updateUiState() {
        if (!isAdded) return
        isUpdatingUi = true
        try {
            val currentMode = modeManager.getAudioEffectMode()
            val isCustom = (currentMode == AudioConstants.MODE_CUSTOM)

            // 1. Cap nhat Hero Card
            if (isCustom) {
                chipCustom.isChecked = true
                tvModeTitle.setText(R.string.sound_enhance_mode_custom_title)
                tvModeSummary.setText(R.string.sound_enhance_mode_custom_desc)
                badgeModeStatus.setText(R.string.sound_enhance_badge_manual)
                updateHeroIllustration(AudioConstants.MODE_CUSTOM)
            } else {
                chipRecommended.isChecked = true
                tvModeTitle.setText(R.string.sound_enhance_mode_recommended_title)
                tvModeSummary.setText(R.string.sound_enhance_mode_recommended_desc)
                badgeModeStatus.setText(R.string.sound_enhance_active_badge)
                imgIllustration.setImageResource(R.drawable.effect_recommendation)
            }

            // 2. Cap nhat gia tri cong tac theo logic loai tru stock (DSEE vs 360)
            switchDolby.isChecked = dolbyController.isEnabled()
            val dseeOn = dseeController.isEnabled()
            val spatialOn = spatialController.isEnabled()
            if (dseeOn && spatialOn) {
                // Neu ca hai deu dang bat, uu tien 360 va tat DSEE theo chuan stock
                dseeController.setEnabled(false)
                switchDsee.isChecked = false
                switchThreesixty.isChecked = true
            } else {
                switchDsee.isChecked = dseeOn
                switchThreesixty.isChecked = spatialOn
            }

            // Cap nhat subtitle cua 360 Upmix kem theo ten mode hien tai
            if (switchThreesixty.isChecked) {
                val profileName = when (spatialController.getProfile()) {
                    SpatialAudioController.UPMIX_PROFILE_CINEMA -> getString(R.string.sound_enhance_upmix_cinema)
                    SpatialAudioController.UPMIX_PROFILE_MUSIC -> getString(R.string.sound_enhance_upmix_music)
                    else -> getString(R.string.sound_enhance_upmix_auto)
                }
                tvThreesixtySummary.text =
                    "${getString(R.string.sound_enhance_threesixty_summary)} • $profileName"
            } else {
                tvThreesixtySummary.setText(R.string.sound_enhance_threesixty_summary)
            }

            // 3. Logic vo hieu hoa cac cong tac phia duoi khi De xuat active
            setFeatureControlsState(isCustom)

            // 4. Wind Noise Reduction (doc lap voi playback mode)
            val windSupported = windController.isSupported()
            recordingPreference?.isVisible = windSupported
            if (windSupported) {
                switchWindNr.isChecked = windController.isEnabled()
            }

            // 5. Dem ung dung trong Effect Priority
            val count = priorityManager.getActivePriorityAppCount()
            tvPriorityCount.text = getString(R.string.sound_enhance_priority_apps_count, count)
        } finally {
            isUpdatingUi = false
        }
    }

    // Vo hieu hoa hoac kich hoat cac cong tac va vung bam theo che do
    private fun setFeatureControlsState(isCustom: Boolean) {
        switchDolby.isEnabled = isCustom
        switchDsee.isEnabled = isCustom
        switchThreesixty.isEnabled = isCustom

        btnDolbyToggleArea.isEnabled = isCustom
        btnDseeToggleArea.isEnabled = isCustom
        btnThreesixtyToggleArea.isEnabled = isCustom

        val alphaValue = if (isCustom) 1.0f else 0.45f
        btnDolbyDetail.alpha = alphaValue
        btnDolbyToggleArea.alpha = alphaValue
        btnDseeDetail.alpha = alphaValue
        btnDseeToggleArea.alpha = alphaValue
        btnThreesixtyDetail.alpha = alphaValue
        btnThreesixtyToggleArea.alpha = alphaValue
    }

    private fun updateHeroIllustration(mode: String) {
        if (mode == AudioConstants.MODE_CUSTOM) {
            val dlb = dolbyController.isEnabled()
            val sp = spatialController.isEnabled()
            val ds = dseeController.isEnabled()
            val res = when {
                dlb -> R.drawable.effect_custom_dolby
                sp -> R.drawable.effect_custom_360upmix
                ds -> R.drawable.effect_custom_dsee
                else -> R.drawable.effect_off_plain
            }
            imgIllustration.setImageResource(res)
        } else {
            imgIllustration.setImageResource(R.drawable.effect_recommendation)
        }
    }

    private fun showDolbyDetailBottomSheet() {
        val bottomSheet = DolbyDetailBottomSheet {
            saveCurrentAsCustomSettings()
            updateUiState()
        }
        bottomSheet.show(parentFragmentManager, "DolbyDetailBottomSheet")
    }

    private fun showSpatialDetailBottomSheet() {
        val bottomSheet = SpatialDetailBottomSheet {
            saveCurrentAsCustomSettings()
            updateUiState()
        }
        bottomSheet.show(parentFragmentManager, "SpatialDetailBottomSheet")
    }

    override fun onDestroy() {
        if (observersRegistered) {
            val ctx = context
            if (ctx != null) {
                try {
                    ctx.contentResolver.unregisterContentObserver(dolbySettingsObserver)
                } catch (_: Exception) {
                }
                try {
                    ctx.unregisterReceiver(dolbyIntentReceiver)
                } catch (_: Exception) {
                }
            }
            observersRegistered = false
        }
        if (::dolbyController.isInitialized) {
            dolbyController.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "sound_enhancement_prefs"

        private const val KEY_HERO_MODE = "pref_hero_mode"
        private const val KEY_DOLBY = "pref_dolby"
        private const val KEY_DSEE = "pref_dsee"
        private const val KEY_THREESIXTY = "pref_threesixty"
        private const val KEY_EFFECT_PRIORITY = "pref_effect_priority"
        private const val KEY_RECORDING = "pref_recording"
    }
}
