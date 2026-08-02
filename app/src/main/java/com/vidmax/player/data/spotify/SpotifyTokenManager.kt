package com.vidmax.player.data.spotify

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Centralized Spotify token management. All token refresh operations go through
 * this singleton to prevent race conditions from multiple callers refreshing
 * simultaneously, which can cause redundant API calls and inconsistent state.
 *
 * SharedPreferences ("SpotifyPrefs" ফাইল) ব্যবহার করে — DataStore-এর বদলে।
 */
class SpotifyTokenManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val refreshMutex = Mutex()

    private val _needsReLogin = MutableStateFlow(false)
    val needsReLogin: StateFlow<Boolean> = _needsReLogin.asStateFlow()

    /**
     * Ensures a valid Spotify access token is available. If the current token
     * is expired, acquires the shared mutex and refreshes it via sp_dc cookie.
     * Only one refresh can happen at a time across the entire app.
     *
     * @return true if a valid token is set on [SpotifyClient.accessToken], false otherwise
     */
    suspend fun ensureAuthenticated(): Boolean {
        val accessToken = storedAccessToken()
        val expiry = storedExpiry()

        if (accessToken.isEmpty()) {
            Log.d(TAG, "no token stored")
            return false
        }

        if (System.currentTimeMillis() < expiry) {
            SpotifyClient.accessToken = accessToken
            return true
        }

        return refreshMutex.withLock {
            // Lock পাওয়ার পর আবার পড়ি — অন্য caller হয়তো ইতিমধ্যে refresh করে ফেলেছে
            val freshToken = storedAccessToken()
            val freshExpiry = storedExpiry()

            if (freshToken.isNotEmpty() && System.currentTimeMillis() < freshExpiry) {
                SpotifyClient.accessToken = freshToken
                Log.d(TAG, "token already refreshed by another caller")
                return@withLock true
            }

            val spDc = storedSpDc()
            val spKey = storedSpKey()
            if (spDc.isEmpty()) {
                Log.w(TAG, "no sp_dc cookie available")
                return@withLock false
            }

            Log.d(TAG, "token expired, refreshing via cookie...")

            SpotifyAuth.fetchAccessToken(spDc, spKey).fold(
                onSuccess = { token ->
                    SpotifyClient.accessToken = token.accessToken
                    prefs.edit()
                        .putString(KEY_ACCESS_TOKEN, token.accessToken)
                        .putLong(KEY_TOKEN_EXPIRY, token.accessTokenExpirationTimestampMs)
                        .apply()
                    _needsReLogin.value = false
                    Log.d(TAG, "token refreshed successfully")
                    true
                },
                onFailure = { e ->
                    Log.e(TAG, "token refresh FAILED: ${e.message}")

                    val isCookieExpired = e.message?.contains("anonymous") == true ||
                        e.message?.contains("expired") == true
                    if (isCookieExpired) {
                        Log.w(TAG, "cookie expired, clearing session")
                        prefs.edit()
                            .remove(KEY_ACCESS_TOKEN)
                            .remove(KEY_SP_DC)
                            .remove(KEY_SP_KEY)
                            .remove(KEY_TOKEN_EXPIRY)
                            .apply()
                        SpotifyClient.accessToken = null
                        _needsReLogin.value = true
                    }

                    false
                },
            )
        }
    }

    /** True when a non-expired access token is stored. */
    fun isLoggedIn(): Boolean {
        val token = storedAccessToken()
        val expiry = storedExpiry()
        return token.isNotEmpty() && System.currentTimeMillis() < expiry
    }

    fun storedAccessToken(): String = prefs.getString(KEY_ACCESS_TOKEN, null) ?: ""

    fun storedExpiry(): Long = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

    fun storedSpDc(): String = prefs.getString(KEY_SP_DC, null) ?: ""

    fun storedSpKey(): String = prefs.getString(KEY_SP_KEY, null) ?: ""

    fun storedUsername(): String = prefs.getString(KEY_USERNAME, null) ?: ""

    fun storedUserId(): String = prefs.getString(KEY_USER_ID, null) ?: ""

    fun saveCookies(spDc: String, spKey: String) {
        prefs.edit()
            .putString(KEY_SP_DC, spDc)
            .putString(KEY_SP_KEY, spKey)
            .apply()
    }

    fun saveToken(accessToken: String, expiryMs: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryMs)
            .apply()
    }

    fun saveProfile(username: String, userId: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun clearReLoginFlag() {
        _needsReLogin.value = false
    }

    /** SharedPreferences instance (home data cache-র জন্যও একই ফাইল ব্যবহৃত হয়)। */
    fun prefs(): SharedPreferences = prefs

    companion object {
        private const val TAG = "SpotifyTokenManager"
        private const val PREFS_NAME = "SpotifyPrefs"

        const val KEY_SP_DC = "sp_dc"
        const val KEY_SP_KEY = "sp_key"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_TOKEN_EXPIRY = "token_expiry"
        const val KEY_USERNAME = "username"
        const val KEY_USER_ID = "user_id"
    }
}
