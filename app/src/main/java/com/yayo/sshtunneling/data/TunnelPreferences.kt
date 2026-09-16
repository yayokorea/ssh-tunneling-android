package com.yayo.sshtunneling.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.ForwardMode
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule
import com.yayo.sshtunneling.model.TunnelAppData
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.model.TunnelDataValidation
import com.yayo.sshtunneling.model.TunnelPhase
import org.json.JSONArray
import org.json.JSONObject

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
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val statusPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
    }

    fun loadAppData(): TunnelAppData {
        val rawJson = securePrefs.getString(KEY_APP_DATA, null) ?: return TunnelAppData()
        return runCatching { parseAppData(rawJson, validate = false) }.getOrElse { TunnelAppData() }
    }

    fun saveAppData(data: TunnelAppData) {
        securePrefs.edit {
            putString(KEY_APP_DATA, exportAppData(data, includeSecrets = true, validate = false))
        }
    }

    /** Export is secret-free by default. Internal encrypted persistence opts in explicitly. */
    fun exportAppData(
        data: TunnelAppData,
        includeSecrets: Boolean = false,
        validate: Boolean = true,
    ): String {
        if (validate) TunnelDataValidation.requireValid(data)
        return JSONObject().apply {
            put(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            put(KEY_SECRETS_INCLUDED, includeSecrets)
            put(KEY_HOSTS, JSONArray().apply {
                data.hosts.forEach { host ->
                    put(JSONObject().apply {
                        put(KEY_ID, host.id)
                        put(KEY_NAME, host.name)
                        put(KEY_HOST, host.host)
                        put(KEY_PORT, host.port)
                        put(KEY_USERNAME, host.username)
                        put(KEY_AUTH_MODE, host.authMode.name)
                        if (includeSecrets) {
                            put(KEY_PASSWORD, host.password)
                            put(KEY_PRIVATE_KEY, host.privateKey)
                        }
                        put(KEY_KEEP_ALIVE, host.keepAliveSeconds)
                        host.hostKeyFingerprint?.takeIf { it.isNotBlank() }?.let {
                            put(KEY_HOST_KEY_FINGERPRINT, it)
                        }
                    })
                }
            })
            put(KEY_FORWARDS, JSONArray().apply {
                data.forwards.forEach { forward ->
                    put(JSONObject().apply {
                        put(KEY_ID, forward.id)
                        put(KEY_HOST_ID, forward.hostId)
                        put(KEY_NAME, forward.name)
                        put(KEY_LOCAL_PORT, forward.localPort)
                        put(KEY_REMOTE_HOST, forward.remoteHost)
                        put(KEY_REMOTE_PORT, forward.remotePort)
                        put(KEY_MODE, forward.mode.name)
                        put(KEY_REVERSE_BIND_HOST, forward.reverseBindHost)
                        put(KEY_REVERSE_BIND_PORT, forward.reverseBindPort)
                        if (forward.widgetSlot != null) {
                            put(KEY_WIDGET_SLOT, forward.widgetSlot)
                        }
                    })
                }
            })
        }.toString()
    }

    fun parseAppData(rawJson: String, validate: Boolean = true): TunnelAppData {
        val root = JSONObject(rawJson)
        val schemaVersion = root.optInt(KEY_SCHEMA_VERSION, LEGACY_SCHEMA_VERSION)
        require(schemaVersion in LEGACY_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "Unsupported settings schema version: $schemaVersion"
        }
        val hosts = root.optJSONArray(KEY_HOSTS)?.toHostProfiles().orEmpty()
        val forwards = root.optJSONArray(KEY_FORWARDS)?.toForwardRules().orEmpty()
        val data = TunnelAppData(hosts = hosts, forwards = forwards)
        return if (validate) TunnelDataValidation.requireValid(data) else data
    }

    fun loadStatuses(): Map<String, ForwardStatus> {
        val rawJson = statusPrefs.getString(KEY_STATUSES, null) ?: return emptyMap()
        val root = runCatching { JSONObject(rawJson) }.getOrElse { return emptyMap() }
        val statuses = mutableMapOf<String, ForwardStatus>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val forwardId = keys.next()
            val item = root.optJSONObject(forwardId) ?: continue
            val savedState = runCatching {
                TunnelConnectionState.valueOf(item.optString(KEY_STATE, TunnelConnectionState.IDLE.name))
            }.getOrDefault(TunnelConnectionState.IDLE)
            val state = when (savedState) {
                TunnelConnectionState.CONNECTED,
                TunnelConnectionState.CONNECTING -> TunnelConnectionState.IDLE
                TunnelConnectionState.IDLE,
                TunnelConnectionState.ERROR -> savedState
            }
            val savedPhase = runCatching {
                TunnelPhase.valueOf(item.optString(KEY_PHASE, TunnelPhase.IDLE.name))
            }.getOrDefault(TunnelPhase.IDLE)
            statuses[forwardId] = ForwardStatus(
                forwardId = forwardId,
                state = state,
                phase = if (state == TunnelConnectionState.IDLE) TunnelPhase.IDLE else savedPhase,
                message = if (state == savedState) item.optString(KEY_MESSAGE).takeIf { it.isNotBlank() } else null,
            )
        }
        return statuses
    }

    fun saveStatuses(statuses: Map<String, ForwardStatus>) {
        val json = JSONObject().apply {
            statuses.values.forEach { status ->
                put(status.forwardId, JSONObject().apply {
                    put(KEY_STATE, status.state.name)
                    put(KEY_PHASE, status.phase.name)
                    put(KEY_MESSAGE, status.message)
                })
            }
        }

        statusPrefs.edit {
            putString(KEY_STATUSES, json.toString())
        }
    }

    private fun JSONArray.toHostProfiles(): List<HostProfile> {
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    HostProfile(
                        id = item.optString(KEY_ID),
                        name = item.optString(KEY_NAME),
                        host = item.optString(KEY_HOST),
                        port = item.optInt(KEY_PORT, 22),
                        username = item.optString(KEY_USERNAME),
                        authMode = runCatching {
                            AuthMode.valueOf(item.optString(KEY_AUTH_MODE, AuthMode.PASSWORD.name))
                        }.getOrDefault(AuthMode.PASSWORD),
                        password = item.optString(KEY_PASSWORD),
                        privateKey = item.optString(KEY_PRIVATE_KEY),
                        keepAliveSeconds = item.optInt(KEY_KEEP_ALIVE, 30),
                        hostKeyFingerprint = item.optString(KEY_HOST_KEY_FINGERPRINT)
                            .takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    private fun JSONArray.toForwardRules(): List<PortForwardRule> {
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    ForwardMode.valueOf(item.optString(KEY_MODE, ForwardMode.LOCAL.name)).let { mode ->
                    PortForwardRule(
                        id = item.optString(KEY_ID),
                        hostId = item.optString(KEY_HOST_ID),
                        name = item.optString(KEY_NAME),
                        localPort = item.optInt(KEY_LOCAL_PORT, 8080),
                        remoteHost = item.optString(KEY_REMOTE_HOST, "127.0.0.1"),
                        remotePort = item.optInt(KEY_REMOTE_PORT, 80),
                        widgetSlot = item.takeIf { it.has(KEY_WIDGET_SLOT) }?.optInt(KEY_WIDGET_SLOT),
                        mode = mode,
                        reverseBindHost = item.optString(KEY_REVERSE_BIND_HOST, PortForwardRule.LOOPBACK_HOST),
                        reverseBindPort = item.optInt(KEY_REVERSE_BIND_PORT, mode.defaultReversePort),
                    )
                    }
                )
            }
        }
    }

    companion object {
        private const val SECURE_PREFS = "tunnel_secure_prefs"
        private const val STATUS_PREFS = "tunnel_status_prefs"

        private const val KEY_APP_DATA = "app_data"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_SECRETS_INCLUDED = "secrets_included"
        private const val KEY_HOSTS = "hosts"
        private const val KEY_FORWARDS = "forwards"
        private const val KEY_STATUSES = "statuses"

        private const val KEY_ID = "id"
        private const val KEY_HOST_ID = "host_id"
        private const val KEY_NAME = "name"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_HOST_KEY_FINGERPRINT = "host_key_fingerprint"
        private const val KEY_LOCAL_PORT = "local_port"
        private const val KEY_REMOTE_HOST = "remote_host"
        private const val KEY_REMOTE_PORT = "remote_port"
        private const val KEY_WIDGET_SLOT = "widget_slot"
        private const val KEY_MODE = "mode"
        private const val KEY_REVERSE_BIND_HOST = "reverse_bind_host"
        private const val KEY_REVERSE_BIND_PORT = "reverse_bind_port"
        private const val KEY_STATE = "state"
        private const val KEY_PHASE = "phase"
        private const val KEY_MESSAGE = "message"

        private const val LEGACY_SCHEMA_VERSION = 1
        private const val CURRENT_SCHEMA_VERSION = 2
    }
}
