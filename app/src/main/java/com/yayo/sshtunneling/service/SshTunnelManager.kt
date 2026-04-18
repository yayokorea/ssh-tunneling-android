package com.yayo.sshtunneling.service

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule

class SshTunnelManager(
    private val host: HostProfile,
    private val forward: PortForwardRule,
) {
    private var session: Session? = null

    fun connect(): Int {
        val jsch = JSch()
        if (host.authMode == AuthMode.PRIVATE_KEY) {
            jsch.addIdentity(
                host.id,
                host.privateKey.toByteArray(Charsets.UTF_8),
                null,
                null,
            )
        }

        val createdSession = jsch.getSession(host.username, host.host, host.port).apply {
            if (host.authMode == AuthMode.PASSWORD) {
                setPassword(host.password)
            }
            setConfig("StrictHostKeyChecking", "no")
            serverAliveInterval = host.keepAliveSeconds * 1000
            timeout = 15_000
            connect(15_000)
        }

        val assignedPort = createdSession.setPortForwardingL(
            forward.localPort,
            forward.remoteHost,
            forward.remotePort,
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
