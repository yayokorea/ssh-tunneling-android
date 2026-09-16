package com.yayo.sshtunneling.adb

import java.net.InetAddress

object AddressMatcher {
    fun isSelf(
        candidateAddresses: Collection<InetAddress>,
        localAddresses: Collection<InetAddress>,
    ): Boolean = candidateAddresses.any { candidate ->
        localAddresses.any { local -> candidate.address.contentEquals(local.address) }
    }
}

object AdbEndpointSelector {
    fun select(
        endpoints: Collection<AdbEndpoint>,
        kind: AdbServiceKind,
    ): AdbEndpoint? {
        val candidates = endpoints.filter {
            it.kind == kind && it.isSelf && it.tcpReachable == true && it.port in 1..65535
        }
        return candidates.singleOrNull()
    }
}
