package com.lumen.launcher.lock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/** Optional lock shortcut only. No screen contents, gestures or events are inspected. */
class ScreenLockService : AccessibilityService() {
    override fun onServiceConnected() { service = WeakReference(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (service?.get() === this) service = null; super.onDestroy() }
    companion object {
        private var service: WeakReference<ScreenLockService>? = null
        fun lock(): Boolean = android.os.Build.VERSION.SDK_INT >= 28 &&
            service?.get()?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
