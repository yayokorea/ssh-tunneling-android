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

data class ForwardStatus(
    val forwardId: String,
    val state: TunnelConnectionState = TunnelConnectionState.IDLE,
    val message: String? = null,
)

data class HostProfile(
    val id: String,
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val keepAliveSeconds: Int = 30,
) {
    fun isComplete(): Boolean {
        val hasAuth = when (authMode) {
            AuthMode.PASSWORD -> password.isNotBlank()
            AuthMode.PRIVATE_KEY -> privateKey.isNotBlank()
        }

        return name.isNotBlank() &&
            host.isNotBlank() &&
            username.isNotBlank() &&
            port > 0 &&
            keepAliveSeconds > 0 &&
            hasAuth
    }
}

data class PortForwardRule(
    val id: String,
    val hostId: String,
    val name: String = "",
    val localPort: Int = 8080,
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int = 80,
    val widgetSlot: Int? = null,
) {
    fun isComplete(): Boolean {
        return name.isNotBlank() &&
            remoteHost.isNotBlank() &&
            localPort > 0 &&
            remotePort > 0
    }
}

data class TunnelAppData(
    val hosts: List<HostProfile> = emptyList(),
    val forwards: List<PortForwardRule> = emptyList(),
)

object WidgetSlots {
    const val COUNT = 6
}
