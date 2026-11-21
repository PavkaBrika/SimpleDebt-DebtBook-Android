package com.breckneck.debtbook.settings

import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

internal fun requirePredictiveGestures(context: Context) {
    assumeTrue(Build.VERSION.SDK_INT >= 35)
    assumeTrue(Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2)
}

/** Keep a real system pointer down while the caller inspects the predictive destination. */
internal fun Instrumentation.edgeSwipe(cancel: Boolean, inspectPreview: () -> Unit) {
    val metrics = targetContext.resources.displayMetrics
    val y = metrics.heightPixels * .42f
    val end = metrics.widthPixels * .60f
    val down = SystemClock.uptimeMillis()
    fun touch(action: Int, x: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    touch(MotionEvent.ACTION_DOWN, 1f)
    try {
        for (step in 1..20) {
            SystemClock.sleep(20)
            touch(MotionEvent.ACTION_MOVE, end * step / 20)
        }
        SystemClock.sleep(250)
        inspectPreview()
        if (cancel) {
            for (step in 19 downTo 0) {
                SystemClock.sleep(20)
                touch(MotionEvent.ACTION_MOVE, 1f + end * step / 20)
            }
            SystemClock.sleep(160)
        }
    } finally {
        touch(MotionEvent.ACTION_UP, if (cancel) 1f else end)
    }
}
