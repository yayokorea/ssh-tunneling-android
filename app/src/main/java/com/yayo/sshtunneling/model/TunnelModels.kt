package com.yayo.sshtunneling.model

enum class AuthMode {
    PASSWORD,
    PRIVATE_KEY,
}

enum class ForwardMode(val defaultReversePort: Int) {
    LOCAL(0),
    ADB_CONNECT(5555),
    ADB_PAIRING(5556),
}

enum class TunnelPhase {
    IDLE,
    WAITING_FOR_PERMISSION,
    WAITING_FOR_WIFI,
    DISCOVERING_ADB,
    PROBING_ADB,
    CONNECTING_SSH,
    BINDING_FORWARD,
    CONNECTED,
    WAITING_FOR_PAIRING,
    RECONNECTING,
    ERROR,
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
    val phase: TunnelPhase = TunnelPhase.IDLE,
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
    val hostKeyFingerprint: String? = null,
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
    val mode: ForwardMode = ForwardMode.LOCAL,
    val reverseBindHost: String = "127.0.0.1",
    val reverseBindPort: Int = mode.defaultReversePort,
) {
    fun isComplete(): Boolean {
        val localForwardIsValid = localPort in 1..65535 &&
            remoteHost.isNotBlank() &&
            remotePort in 1..65535
        val reverseForwardIsValid = reverseBindHost == LOOPBACK_HOST &&
            reverseBindPort in 1..65535
        return name.isNotBlank() && localForwardIsValid &&
            (mode == ForwardMode.LOCAL || reverseForwardIsValid)
    }

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
    }
}

data class TunnelAppData(
    val hosts: List<HostProfile> = emptyList(),
    val forwards: List<PortForwardRule> = emptyList(),
)

object WidgetSlots {
    const val COUNT = 6
}
