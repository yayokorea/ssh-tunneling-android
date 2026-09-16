package com.yayo.sshtunneling.model

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
    fun rejectsUnknownHostReference() {
        val data = TunnelAppData(
            hosts = listOf(host),
            forwards = listOf(PortForwardRule(id = "forward-1", hostId = "missing")),
        )

        assertTrue(TunnelDataValidation.errors(data).any { it.contains("host_id") })
    }
}
