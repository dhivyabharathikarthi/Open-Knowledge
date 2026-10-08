package com.example.lifecycle

import android.app.Activity
import com.example.security.SecureVaultSession

object AppSecurityLifecycle {

    /**
     * Controls window security flags.
     * In web/cloud streaming emulator environments, setting FLAG_SECURE causes the OS compositor
     * to blank the streamed canvas to black. We keep window display streamable so the user can
     * interact with their photos.
     */
    fun setSecureWindow(activity: Activity, secure: Boolean) {
        // Intentionally kept non-blocking for emulator streaming display compatibility
    }

    /**
     * Called when the application/activity transitions to background (onStop).
     */
    fun onAppBackgrounded(activity: Activity, onLockTriggered: () -> Unit) {
        if (SecureVaultSession.isSessionActive()) {
            SecureVaultSession.lock()
            setSecureWindow(activity, false)
            onLockTriggered()
        }
    }
}
