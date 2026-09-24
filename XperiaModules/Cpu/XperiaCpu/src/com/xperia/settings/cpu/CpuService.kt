/*
 * Copyright (C) 2026 XperiaLabs Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xperia.settings.cpu

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Lang nghe SCREEN_ON/OFF + Settings.Global, ap dung CpuUtils.
 * Re-apply dinh ky de chong PowerHAL/thermal ghi de scaling_max.
 */
class CpuService : Service() {

    private lateinit var utils: CpuUtils
    private val handler = Handler(Looper.getMainLooper())
    private var settingsObserver: ContentObserver? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF -> utils.applyCurrent()
            }
        }
    }

    private val reapplyRunnable = object : Runnable {
        override fun run() {
            utils.applyCurrent()
            handler.postDelayed(this, REAPPLY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        utils = CpuUtils(this)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            RECEIVER_NOT_EXPORTED
        )
        settingsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                utils.applyCurrent()
            }
        }.also { obs ->
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(CpuUtils.KEY_IDLE_MODE), false, obs
            )
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(CpuUtils.KEY_ACTIVE_LIMIT), false, obs
            )
        }
        utils.applyCurrent()
        handler.postDelayed(reapplyRunnable, REAPPLY_MS)
        Log.i(TAG, "CpuService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        utils.applyCurrent()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(reapplyRunnable)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        settingsObserver?.let { contentResolver.unregisterContentObserver(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "XperiaCpu"
        private const val REAPPLY_MS = 60_000L

        fun start(context: Context) {
            context.startService(Intent(context, CpuService::class.java))
        }
    }
}
