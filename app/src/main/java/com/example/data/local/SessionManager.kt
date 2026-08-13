package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shipu_ai_session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "active_user_id"
        private const val KEY_SESSION_TOKEN = "active_session_token"
        private const val KEY_USER_NAME = "active_user_name"
        private const val KEY_USER_EMAIL = "active_user_email"
    }

    fun saveSession(userId: String, token: String, name: String, email: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_SESSION_TOKEN, token)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getActiveUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getActiveToken(): String? = prefs.getString(KEY_SESSION_TOKEN, null)
    fun getActiveUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getActiveUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
