package com.sonyericsson.soundenhancement.ui

import android.content.Context
import android.util.AttributeSet
import com.android.settingslib.widget.LayoutPreference
import com.android.settingslib.widget.NormalPaddingMixin

// Giu nguyen padding ngang 0 cua card trong SettingsPreferenceGroupAdapter Expressive
class SoundLayoutPreference : LayoutPreference, NormalPaddingMixin {

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )
}
