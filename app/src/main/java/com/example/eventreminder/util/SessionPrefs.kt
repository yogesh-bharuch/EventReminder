package com.example.eventreminder.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SessionPrefs {
    private const val FILE = "secure_prefs"
    private const val KEY_UID = "uid"
    private const val KEY_EMAIL = "email"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun save(context: Context, uid: String, email: String?) {
        prefs(context).edit()
            .putString(KEY_UID, uid)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getUid(context: Context): String? =
        prefs(context).getString(KEY_UID, null)

    fun getEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}