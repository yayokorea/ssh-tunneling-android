package com.yayo.sshtunneling.adb

import android.net.Network
import java.net.InetAddress

data class AdbEndpoint(
    val kind: AdbServiceKind,
    val instanceName: String,
    val addresses: List<InetAddress>,
    val port: Int,
    val network: Network? = null,
    val isSelf: Boolean = false,
    val tcpReachable: Boolean? = null,
) {
    val primaryAddress: InetAddress?
        get() = addresses.firstOrNull()
}

data class AdbDiscoverySnapshot(
    val endpoints: List<AdbEndpoint> = emptyList(),
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
)
