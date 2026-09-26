package com.yayo.sshtunneling.service

internal object TunnelRetryPolicy {
    const val INITIAL_DELAY_MILLIS = 1_000L
    private const val MAX_DELAY_MILLIS = 60_000L

    fun nextDelayMillis(current: Long): Long = (current * 2).coerceAtMost(MAX_DELAY_MILLIS)

    fun isPermanentSshFailure(message: String): Boolean =
        message.contains("Auth fail", ignoreCase = true) ||
            message.contains("HostKey", ignoreCase = true) ||
            message.contains("port forwarding failed", ignoreCase = true) ||
            message.contains("cannot be bound", ignoreCase = true) ||
            message.contains("Address already in use", ignoreCase = true) ||
            message.contains("invalid privatekey", ignoreCase = true)
}
