package com.yayo.sshtunneling.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelDataValidationTest {
    private val host = HostProfile(id = "host-1")

    @Test
    fun acceptsLegacyLocalForwardDefaults() {
        val data = TunnelAppData(
            hosts = listOf(host),
            forwards = listOf(PortForwardRule(id = "forward-1", hostId = host.id)),
        )

        assertTrue(TunnelDataValidation.errors(data).isEmpty())
    }

    @Test
    fun hostCompletenessRejectsOutOfRangeNumbers() {
        val completeHost = HostProfile(
            id = "host-1",
            name = "Server",
            host = "example.com",
            username = "user",
            password = "secret",
        )

        assertTrue(completeHost.isComplete())
        assertFalse(completeHost.copy(port = 65_536).isComplete())
        assertFalse(completeHost.copy(keepAliveSeconds = 86_401).isComplete())
    }

    @Test
    fun hostCompletenessAllowsExplicitNoAuthentication() {
        val hostWithoutCredentials = HostProfile(
            id = "host-1",
            name = "Server",
            host = "example.com",
            username = "user",
            authMode = AuthMode.NONE,
        )

        assertTrue(hostWithoutCredentials.isComplete())
    }

    @Test
    fun requiresLoopbackForAdbReverseForward() {
        val forward = PortForwardRule(
            id = "forward-1",
            hostId = host.id,
            mode = ForwardMode.ADB_CONNECT,
            reverseBindHost = "0.0.0.0",
            reverseBindPort = 5555,
        )

        assertTrue(TunnelDataValidation.errors(TunnelAppData(listOf(host), listOf(forward))).isNotEmpty())
    }

    @Test
    fun adbForwardDoesNotRequireUnusedLocalForwardFields() {
        val forward = PortForwardRule(
            id = "forward-1",
            hostId = host.id,
            name = "ADB connect",
            mode = ForwardMode.ADB_CONNECT,
            localPort = 0,
            remoteHost = "",
            remotePort = 0,
            reverseBindPort = 5555,
        )

        assertTrue(TunnelDataValidation.errors(TunnelAppData(listOf(host), listOf(forward))).isEmpty())
        assertTrue(forward.isComplete())
    }

    @Test
    fun rejectsUnknownHostReference() {
        val data = TunnelAppData(
            hosts = listOf(host),
            forwards = listOf(PortForwardRule(id = "forward-1", hostId = "missing")),
        )

        assertTrue(TunnelDataValidation.errors(data).any { it.contains("host_id") })
    }
}
