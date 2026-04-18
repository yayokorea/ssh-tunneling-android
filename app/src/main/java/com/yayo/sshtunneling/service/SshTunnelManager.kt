package com.yayo.sshtunneling.service

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.TunnelProfile

class SshTunnelManager(
    private val profile: TunnelProfile,
) {
    private var session: Session? = null

    fun connect(): Int {
        val jsch = JSch()
        if (profile.authMode == AuthMode.PRIVATE_KEY) {
            jsch.addIdentity(
                "inline-key",
                profile.privateKey.toByteArray(Charsets.UTF_8),
                null,
                null,
            )
        }

        val createdSession = jsch.getSession(profile.username, profile.host, profile.port).apply {
            if (profile.authMode == AuthMode.PASSWORD) {
                setPassword(profile.password)
            }
            setConfig("StrictHostKeyChecking", "no")
            serverAliveInterval = profile.keepAliveSeconds * 1000
            timeout = 15_000
            connect(15_000)
        }

        val assignedPort = createdSession.setPortForwardingL(
            profile.localPort,
            profile.remoteHost,
            profile.remotePort,
        )
        session = createdSession
        return assignedPort
    }

    fun disconnect() {
        session?.disconnect()
        session = null
    }

    fun isConnected(): Boolean = session?.isConnected == true
}
