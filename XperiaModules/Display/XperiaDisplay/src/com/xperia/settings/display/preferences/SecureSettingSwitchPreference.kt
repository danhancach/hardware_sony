/*
* Copyright (C) 2024 XperiaLabs Project
* Copyright (C) 2016-2018 crDroid Android Project
*
 * SPDX-License-Identifier: Apache-2.0
*/

package com.xperia.settings.preferences

import android.content.Context
import android.provider.Settings
import android.os.UserHandle
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat

class SecureSettingSwitchPreference : SwitchPreferenceCompat {

   constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)
   constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
   constructor(context: Context) : super(context)

   override fun persistBoolean(value: Boolean): Boolean {
       putBoolean(key, value)
       return true
   }

   private fun putBoolean(key: String?, value: Boolean) {
       if (key != null) {
           Settings.Secure.putIntForUser(context.contentResolver, key, if (value) 1 else 0, UserHandle.USER_CURRENT)
       }
   }

   override fun getPersistedBoolean(defaultReturnValue: Boolean): Boolean {
       return getBoolean(key, defaultReturnValue)
   }

   private fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
       return if (key != null) {
           Settings.Secure.getIntForUser(context.contentResolver, key, if (defaultValue) 1 else 0, UserHandle.USER_CURRENT) != 0
       } else {
           defaultValue
       }
   }
}