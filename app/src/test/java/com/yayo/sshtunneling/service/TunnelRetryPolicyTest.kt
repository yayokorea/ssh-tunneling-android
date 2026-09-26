package com.yayo.sshtunneling.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRetryPolicyTest {
    @Test
    fun retryDelayDoublesAndStopsAtOneMinute() {
        var delay = TunnelRetryPolicy.INITIAL_DELAY_MILLIS
        val observed = buildList {
            repeat(8) {
                add(delay)
                delay = TunnelRetryPolicy.nextDelayMillis(delay)
            }
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), observed)
    }

    @Test
    fun authenticationAndTrustFailuresDoNotRetry() {
        assertTrue(TunnelRetryPolicy.isPermanentSshFailure("Auth fail"))
        assertTrue(TunnelRetryPolicy.isPermanentSshFailure("reject HostKey"))
        assertTrue(TunnelRetryPolicy.isPermanentSshFailure("remote port forwarding failed"))
        assertTrue(TunnelRetryPolicy.isPermanentSshFailure("Address already in use"))
        assertFalse(TunnelRetryPolicy.isPermanentSshFailure("connection timed out"))
        assertFalse(TunnelRetryPolicy.isPermanentSshFailure("network is unreachable"))
    }
}
