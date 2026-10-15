package com.vision.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted settings for API key and voice. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (::sp.isInitialized) return
        val app = c.applicationContext
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        sp = EncryptedSharedPreferences.create(
            app, "star_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var apiKey: String
        get() = sp.getString("api", "") ?: ""
        set(v) = sp.edit().putString("api", v.trim()).apply()

    var voice: String
        get() = sp.getString("voice", "Charon") ?: "Charon"
        set(v) = sp.edit().putString("voice", v.trim()).apply()

}
