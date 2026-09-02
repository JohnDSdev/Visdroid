package com.johndsdev.visdroid

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process bridge between the foreground playback-capture service and the live wallpaper.
 * The wallpaper and service normally live in the same application process, so an atomic bus
 * avoids binder churn at 30-60 updates/second.
 */
object PlaybackSpectrumBus {
    val active = AtomicBoolean(false)
    val levels = AtomicReference(FloatArray(0))
    val status = AtomicReference("off")

    fun publish(values: FloatArray) {
        levels.set(values)
    }

    fun clear(statusText: String = "off") {
        active.set(false)
        levels.set(FloatArray(0))
        status.set(statusText)
    }
}
