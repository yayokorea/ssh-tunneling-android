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

    @Synchronized
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
            if (this@SshTunnelManager.host.authMode == AuthMode.PASSWORD) {
                setPassword(this@SshTunnelManager.host.password)
            }
            setConfig("StrictHostKeyChecking", "no")
            serverAliveInterval = this@SshTunnelManager.host.keepAliveSeconds * 1000
            timeout = 15_000
            connect(15_000)
        }

        return runCatching {
            createdSession.setPortForwardingL(
                forward.localPort,
                forward.remoteHost,
                forward.remotePort,
            )
        }.onSuccess {
            session = createdSession
        }.onFailure {
            createdSession.disconnect()
        }.getOrThrow()
    }

    @Synchronized
    fun disconnect() {
        session?.disconnect()
        session = null
    }

    @Synchronized
    fun isConnected(): Boolean = session?.isConnected == true

    @Synchronized
    fun verifyConnected(): Boolean {
        val currentSession = session ?: return false
        if (!currentSession.isConnected) return false
        return runCatching {
            currentSession.sendKeepAliveMsg()
            currentSession.isConnected
        }.getOrDefault(false)
    }
}
