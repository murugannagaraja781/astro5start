package com.astro5star.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.astro5star.app.data.model.AuthResponse
import com.google.gson.Gson

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserSession(auth: AuthResponse) {
        val json = Gson().toJson(auth)
        sharedPreferences.edit().putString("user_session", json).apply()
    }

    fun getUserSession(): AuthResponse? {
        val json = sharedPreferences.getString("user_session", null) ?: return null
        return try {
            Gson().fromJson(json, AuthResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getUserSession() != null
    }

    fun updateWalletBalance(balance: Double) {
        val session = getUserSession() ?: return
        val updated = session.copy(walletBalance = balance)
        saveUserSession(updated)
    }
}
