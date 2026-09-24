/*
 * Copyright (C) 2024 XperiaLabs Project
 * Copyright (C) 2018 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.touch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;

import com.xperia.settings.touch.polling.HighTouchPollingService;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "XperiaTouch";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(TAG, "Received intent: " + intent.getAction());

        switch (intent.getAction()) {
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                onLockedBootCompleted(context);
                break;
        }
    }

    private static void onLockedBootCompleted(Context context) {
        // Services that don't require reading from data.
        HighTouchPollingService.startService(context);
    }
}
