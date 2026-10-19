package com.sonyericsson.soundenhancement.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.backend.AudioConstants
import com.sonyericsson.soundenhancement.backend.AudioModeManager
import com.sonyericsson.soundenhancement.backend.DseeController
import com.sonyericsson.soundenhancement.backend.SpatialAudioController

// Dialog tuy chinh chi tiet 360 Upmix theo phong cach M3 Expressive
class SpatialDetailBottomSheet(
    private val onProfileChanged: ((Int) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private lateinit var modeManager: AudioModeManager
    private lateinit var spatialController: SpatialAudioController
    private lateinit var dseeController: DseeController

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
        val btnOpen360App = view.findViewById<MaterialButton>(R.id.btn_open_360_app)

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

            // Kich hoat 360 Upmix voi profile da chon
            spatialController.setEnabled(true, profile)

            // Theo logic stock: 360 va DSEE chi hoat dong cung luc mot mode, bat 360 phai tat DSEE
            if (dseeController.isEnabled()) {
                dseeController.setEnabled(false)
            }

            modeManager.setCustomProfile(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_UPMIX_PROFILE, profile.toString())
            modeManager.setCustomEffect(
                dolby = modeManager.getCustomProfile(AudioConstants.KEY_DB_AUDIOEFFECT_CUSTOM_EFFECT, "").contains("dolby"),
                upmix = true,
                dsee = false
            )
            onProfileChanged?.invoke(profile)
        }

        btnOpen360App.setOnClickListener {
            val launched = tryLaunchIntent(Intent("jp.co.sony.threesixtyra.settings.ACTION_UPMIX")) ||
                    tryLaunchComponent("jp.co.sony.threesixtyra.settings", "jp.co.sony.threesixtyra.settings.settingsmenu.SettingsMenuActivity") ||
                    tryLaunchComponent("jp.co.sony.threesixtyra.settings", "jp.co.sony.threesixtyra.settings.LauncherActivity") ||
                    tryLaunchPackage("jp.co.sony.threesixtyra.settings")
            if (!launched) {
                Toast.makeText(ctx, "Khong tim thay ung dung 360 Reality Audio Settings", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun tryLaunchPackage(pkg: String): Boolean {
        return try {
            val pm = requireContext().packageManager
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
