package com.yayo.sshtunneling.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AddressMatcherTest {
    @Test
    fun comparesAddressBytesInsteadOfHostNames() {
        val resolved = InetAddress.getByName("192.168.1.20")
        val local = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 20))

        assertTrue(AddressMatcher.isSelf(listOf(resolved), listOf(local)))
    }

    @Test
    fun rejectsDifferentAddress() {
        assertFalse(
            AddressMatcher.isSelf(
                listOf(InetAddress.getByName("192.168.1.20")),
                listOf(InetAddress.getByName("192.168.1.21")),
            ),
        )
    }

    @Test
    fun refusesAmbiguousSelfCandidates() {
        val address = InetAddress.getByName("192.168.1.20")
        val candidates = listOf(
            AdbEndpoint(AdbServiceKind.CONNECT, "one", listOf(address), 37123, isSelf = true, tcpReachable = true),
            AdbEndpoint(AdbServiceKind.CONNECT, "two", listOf(address), 37124, isSelf = true, tcpReachable = true),
        )

        assertTrue(AdbEndpointSelector.select(candidates, AdbServiceKind.CONNECT) == null)
    }
}
