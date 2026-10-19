package com.sonyericsson.soundenhancement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.backend.AudioConstants
import com.sonyericsson.soundenhancement.backend.AudioModeManager
import com.sonyericsson.soundenhancement.backend.AudioModeTransition
import com.sonyericsson.soundenhancement.backend.DseeController
import com.sonyericsson.soundenhancement.backend.SpatialAudioController

// Dialog tuy chinh chi tiet 360 Upmix theo phong cach M3 Expressive
class SpatialDetailBottomSheet(
    private val onProfileChanged: ((Int) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private lateinit var modeManager: AudioModeManager
    private lateinit var spatialController: SpatialAudioController
    private lateinit var dseeController: DseeController
    private val modeTransition = AudioModeTransition()

    override fun getTheme(): Int = R.style.ThemeOverlay_SoundEnhancement_BottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        configureSoundBottomSheetDialog(resources, dialog, fillMaxHeight = false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_spatial_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        modeManager = AudioModeManager(ctx)
        spatialController = SpatialAudioController(ctx)
        dseeController = DseeController(ctx)

        val radioGroup = view.findViewById<RadioGroup>(R.id.rg_upmix_mode)

        // Doc profile hien tai tu SpatialAudioController
        val currentProfile = spatialController.getProfile()
        when (currentProfile) {
            SpatialAudioController.UPMIX_PROFILE_CINEMA -> radioGroup.check(R.id.rb_upmix_cinema)
            SpatialAudioController.UPMIX_PROFILE_MUSIC -> radioGroup.check(R.id.rb_upmix_music)
            else -> radioGroup.check(R.id.rb_upmix_auto)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val profile = when (checkedId) {
                R.id.rb_upmix_cinema -> SpatialAudioController.UPMIX_PROFILE_CINEMA
                R.id.rb_upmix_music -> SpatialAudioController.UPMIX_PROFILE_MUSIC
                else -> SpatialAudioController.UPMIX_PROFILE_AUTO
            }

            // Neu da bat va cung profile thi bo qua de tranh tieng pop
            if (spatialController.isEnabled() && spatialController.getProfile() == profile) {
                return@setOnCheckedChangeListener
            }

            // Chuyen doi tuan tu em ai: Tat DSEE truoc de AudioFlinger dong DIRECT stream
            if (dseeController.isEnabled()) {
                dseeController.setEnabled(false)
            }

            // Kich hoat 360 Upmix voi profile da chon sau khi DSEE da ngat
            modeTransition.schedule {
                spatialController.setEnabled(true, profile)
                modeManager.setCustomProfile(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_UPMIX_PROFILE, profile.toString())
                modeManager.setCustomEffect(
                    dolby = modeManager.getCustomProfile(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_EFFECT, "").contains("dolby"),
                    upmix = true,
                    dsee = false
                )
                onProfileChanged?.invoke(profile)
            }
        }
    }
}
