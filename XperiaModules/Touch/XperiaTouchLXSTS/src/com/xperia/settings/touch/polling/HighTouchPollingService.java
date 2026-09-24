/*
 * Copyright (C) 2024 XperiaLabs
 * Copyright (C) 2023 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xperia.settings.touch.polling;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;

import lineageos.providers.LineageSettings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dong bo frame_rate_np theo LineageSettings + man hinh / pin tiet kiem.
 * Settings/IMM cung ghi cung key qua LineageHardware HAL; service nay
 * them dieu kien screen-off va battery saver.
 */
public class HighTouchPollingService extends Service {

    private static final String TAG = "HighTouchPollingService";
    private static final boolean DEBUG = false;

    // Trung key Settings Display (LineageSettings, khong phai Settings.System)
    private static final String SETTING_KEY =
            LineageSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE;
    private static final String TS_NODE =
            "/sys/devices/virtual/input/lxs_ts_input/frame_rate_np";
    private static final String RATE_HIGH = "0 3";
    private static final String RATE_NORMAL = "0 2";

    private boolean mEnabled;
    private boolean mScreenOn = true;
    private boolean mPowerSave;
    private PowerManager mPowerManager;

    private final ContentObserver mSettingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateTouchPollingState(true);
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case Intent.ACTION_SCREEN_ON:
                    mScreenOn = true;
                    updateTouchPollingState(false);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOn = false;
                    updateTouchPollingState(false);
                    break;
                case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                    mPowerSave = mPowerManager.isPowerSaveMode();
                    updateTouchPollingState(false);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mPowerManager = getSystemService(PowerManager.class);
        getContentResolver().registerContentObserver(
                LineageSettings.System.getUriFor(SETTING_KEY), false, mSettingObserver);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        mPowerSave = mPowerManager != null && mPowerManager.isPowerSaveMode();
        updateTouchPollingState(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dlog("onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(mSettingObserver);
        unregisterReceiver(mIntentReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, HighTouchPollingService.class),
                UserHandle.CURRENT);
    }

    private void updateTouchPollingState(boolean readSetting) {
        if (readSetting) {
            mEnabled = LineageSettings.System.getIntForUser(
                    getContentResolver(), SETTING_KEY, 0, UserHandle.USER_CURRENT) == 1;
        }

        final String value = mScreenOn && mEnabled && !mPowerSave ? RATE_HIGH : RATE_NORMAL;
        try (FileOutputStream fos = new FileOutputStream(TS_NODE)) {
            fos.write(value.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to touch node", e);
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
