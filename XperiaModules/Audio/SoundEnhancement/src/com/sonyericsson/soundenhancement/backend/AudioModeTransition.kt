package com.sonyericsson.soundenhancement.backend

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicLong

/**
 * Tuan tu hoa chuyen doi audio mode de callback tre khong bat lai route cu
 * trong khi AudioFlinger van dang dong route truoc do.
 */
class AudioModeTransition {
    companion object {
        const val ROUTE_SETTLE_MS = 120L
        const val EFFECT_GAP_MS = 80L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()

    fun schedule(delayMs: Long = ROUTE_SETTLE_MS, action: () -> Unit) {
        val token = generation.incrementAndGet()
        handler.postDelayed({
            if (generation.get() == token) action()
        }, delayMs)
    }

    fun scheduleSequence(first: () -> Unit, second: () -> Unit) {
        val token = generation.incrementAndGet()
        handler.post {
            if (generation.get() != token) return@post
            first()
            handler.postDelayed({
                if (generation.get() == token) second()
            }, EFFECT_GAP_MS)
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        handler.removeCallbacksAndMessages(null)
    }
}
