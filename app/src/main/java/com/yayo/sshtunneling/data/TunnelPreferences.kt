package com.yayo.sshtunneling.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.model.TunnelProfile
import com.yayo.sshtunneling.model.TunnelStatus

class TunnelPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val statusPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
    }

    fun loadProfile(): TunnelProfile {
        return TunnelProfile(
            host = securePrefs.getString(KEY_HOST, "") ?: "",
            port = securePrefs.getInt(KEY_PORT, 22),
            username = securePrefs.getString(KEY_USERNAME, "") ?: "",
            authMode = runCatching {
                AuthMode.valueOf(securePrefs.getString(KEY_AUTH_MODE, AuthMode.PASSWORD.name) ?: AuthMode.PASSWORD.name)
            }.getOrDefault(AuthMode.PASSWORD),
            password = securePrefs.getString(KEY_PASSWORD, "") ?: "",
            privateKey = securePrefs.getString(KEY_PRIVATE_KEY, "") ?: "",
            localPort = securePrefs.getInt(KEY_LOCAL_PORT, 8080),
            remoteHost = securePrefs.getString(KEY_REMOTE_HOST, "127.0.0.1") ?: "127.0.0.1",
            remotePort = securePrefs.getInt(KEY_REMOTE_PORT, 80),
            keepAliveSeconds = securePrefs.getInt(KEY_KEEP_ALIVE, 30)
        )
    }

    fun saveProfile(profile: TunnelProfile) {
        securePrefs.edit {
            putString(KEY_HOST, profile.host)
            putInt(KEY_PORT, profile.port)
            putString(KEY_USERNAME, profile.username)
            putString(KEY_AUTH_MODE, profile.authMode.name)
            putString(KEY_PASSWORD, profile.password)
            putString(KEY_PRIVATE_KEY, profile.privateKey)
            putInt(KEY_LOCAL_PORT, profile.localPort)
            putString(KEY_REMOTE_HOST, profile.remoteHost)
            putInt(KEY_REMOTE_PORT, profile.remotePort)
            putInt(KEY_KEEP_ALIVE, profile.keepAliveSeconds)
        }
    }

    fun loadStatus(): TunnelStatus {
        val state = runCatching {
            TunnelConnectionState.valueOf(
                statusPrefs.getString(KEY_STATE, TunnelConnectionState.IDLE.name) ?: TunnelConnectionState.IDLE.name
            )
        }.getOrDefault(TunnelConnectionState.IDLE)

        return TunnelStatus(
            state = state,
            message = statusPrefs.getString(KEY_MESSAGE, null)
        )
    }

    fun saveStatus(status: TunnelStatus) {
        statusPrefs.edit {
            putString(KEY_STATE, status.state.name)
            putString(KEY_MESSAGE, status.message)
        }
    }

    companion object {
        private const val SECURE_PREFS = "tunnel_secure_prefs"
        private const val STATUS_PREFS = "tunnel_status_prefs"

        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_LOCAL_PORT = "local_port"
        private const val KEY_REMOTE_HOST = "remote_host"
        private const val KEY_REMOTE_PORT = "remote_port"
        private const val KEY_KEEP_ALIVE = "keep_alive"

        private const val KEY_STATE = "state"
        private const val KEY_MESSAGE = "message"
    }
}
