package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "open_knowledge_internal_prefs"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_TRIGGER_KEY = "trigger_key_b64"
        private const val KEY_TRIGGER_VERIFIER = "trigger_verifier_b64"
        private const val KEY_PIN_SALT = "pin_salt_b64"
        private const val KEY_VMK_ENVELOPE = "vmk_envelope_b64"
        private const val KEY_AUTO_LOCK_SECONDS = "auto_lock_seconds"
        private const val KEY_LAST_ACTIVE_TIME = "last_active_time"
    }

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_SECONDS, 300) // 300 = 5 minutes default
        set(value) = prefs.edit().putInt(KEY_AUTO_LOCK_SECONDS, value).apply()

    var lastActiveTime: Long
        get() = prefs.getLong(KEY_LAST_ACTIVE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, value).apply()

    fun saveTriggerData(verifierKey: ByteArray, verifierHash: ByteArray) {
        prefs.edit()
            .putString(KEY_TRIGGER_KEY, Base64.encodeToString(verifierKey, Base64.NO_WRAP))
            .putString(KEY_TRIGGER_VERIFIER, Base64.encodeToString(verifierHash, Base64.NO_WRAP))
            .apply()
    }

    fun getTriggerVerifierKey(): ByteArray? {
        val str = prefs.getString(KEY_TRIGGER_KEY, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    fun getTriggerVerifierHash(): ByteArray? {
        val str = prefs.getString(KEY_TRIGGER_VERIFIER, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    fun savePinSalt(salt: ByteArray) {
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
    }

    fun getPinSalt(): ByteArray? {
        val str = prefs.getString(KEY_PIN_SALT, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    fun saveVmkEnvelope(envelope: ByteArray) {
        prefs.edit()
            .putString(KEY_VMK_ENVELOPE, Base64.encodeToString(envelope, Base64.NO_WRAP))
            .apply()
    }

    fun getVmkEnvelope(): ByteArray? {
        val str = prefs.getString(KEY_VMK_ENVELOPE, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
