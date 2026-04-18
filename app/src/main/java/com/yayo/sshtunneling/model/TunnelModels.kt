package com.yayo.sshtunneling.model

enum class AuthMode {
    PASSWORD,
    PRIVATE_KEY,
}

enum class TunnelConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class TunnelStatus(
    val state: TunnelConnectionState = TunnelConnectionState.IDLE,
    val message: String? = null,
)

data class TunnelProfile(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val localPort: Int = 8080,
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int = 80,
    val keepAliveSeconds: Int = 30,
) {
    fun isComplete(): Boolean {
        val hasAuth = when (authMode) {
            AuthMode.PASSWORD -> password.isNotBlank()
            AuthMode.PRIVATE_KEY -> privateKey.isNotBlank()
        }

        return host.isNotBlank() &&
            username.isNotBlank() &&
            remoteHost.isNotBlank() &&
            port > 0 &&
            localPort > 0 &&
            remotePort > 0 &&
            keepAliveSeconds > 0 &&
            hasAuth
    }
}
