package com.yayo.sshtunneling.service

import android.util.Base64
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.ForwardMode
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule
import java.security.MessageDigest

data class ObservedHostKey(
    val endpoint: String,
    val fingerprint: String,
)

/** Reads the SSH host key before credentials are offered to the server. */
object SshHostKeyProbe {
    fun probe(host: String, port: Int, timeoutMillis: Int = 15_000): ObservedHostKey {
        require(host.isNotBlank()) { "SSH 서버 주소를 입력하세요." }
        require(port in 1..65535) { "SSH 포트는 1~65535 사이여야 합니다." }

        var observedFingerprint: String? = null
        val jsch = JSch().apply {
            hostKeyRepository = object : HostKeyRepository {
                override fun check(host: String?, key: ByteArray): Int {
                    observedFingerprint = PinnedHostKeyRepository.fingerprint(key)
                    return HostKeyRepository.OK
                }

                override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
                override fun remove(host: String?, type: String?) = Unit
                override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
                override fun getKnownHostsRepositoryID(): String = "temporary host key probe"
                override fun getHostKey(): Array<HostKey> = emptyArray()
                override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
            }
        }
        val session = jsch.getSession("host-key-probe", host, port).apply {
            setConfig("StrictHostKeyChecking", "yes")
            setConfig("PreferredAuthentications", "none")
            timeout = timeoutMillis
        }

        try {
            session.connect(timeoutMillis)
        } catch (error: Exception) {
            if (observedFingerprint == null) throw error
        } finally {
            session.disconnect()
        }

        return ObservedHostKey(
            endpoint = "$host:$port",
            fingerprint = checkNotNull(observedFingerprint) { "SSH 서버 키를 가져오지 못했습니다." },
        )
    }
}

/** Owns one SSH session and the forwarding rules attached to it. */
class SshTunnelManager(
    private val host: HostProfile,
    private val forward: PortForwardRule,
) {
    private var session: Session? = null
    private var reverseForwardActive = false

    @Synchronized
    fun connect(): Int {
        val connectedSession = connectSession()
        return runCatching {
            when (forward.mode) {
                ForwardMode.LOCAL -> addLocalForward(connectedSession)
                ForwardMode.ADB_CONNECT,
                ForwardMode.ADB_PAIRING -> error("ADB endpoint is required before reverse forwarding")
            }
        }.onFailure {
            connectedSession.disconnect()
            session = null
        }.getOrThrow()
    }

    @Synchronized
    fun connectSession(): Session {
        session?.takeIf { it.isConnected }?.let { return it }
        val jsch = JSch()
        if (host.authMode == AuthMode.PRIVATE_KEY) {
            jsch.addIdentity(host.id, host.privateKey.toByteArray(Charsets.UTF_8), null, null)
        }

        val expectedFingerprint = host.hostKeyFingerprint?.trim().orEmpty()
        if (expectedFingerprint.isNotBlank()) {
            jsch.hostKeyRepository = PinnedHostKeyRepository(expectedFingerprint)
        } else if (forward.mode != ForwardMode.LOCAL) {
            throw IllegalStateException("ADB 터널은 SSH 서버 fingerprint 확인이 필요합니다.")
        }

        val createdSession = jsch.getSession(host.username, host.host, host.port).apply {
            if (this@SshTunnelManager.host.authMode == AuthMode.PASSWORD) {
                setPassword(this@SshTunnelManager.host.password)
            }
            // Legacy local tunnels remain compatible; ADB tunnels always pin a key.
            setConfig("StrictHostKeyChecking", if (expectedFingerprint.isBlank()) "no" else "yes")
            serverAliveInterval = this@SshTunnelManager.host.keepAliveSeconds * 1000
            serverAliveCountMax = 3
            timeout = 15_000
        }

        return runCatching {
            createdSession.connect(15_000)
            session = createdSession
            createdSession
        }.onFailure { createdSession.disconnect() }.getOrThrow()
    }

    @Synchronized
    fun addLocalForward(connectedSession: Session = requireSession()): Int =
        connectedSession.setPortForwardingL(forward.localPort, forward.remoteHost, forward.remotePort)

    @Synchronized
    fun addReverseForward(address: String, port: Int): Int {
        require(forward.mode != ForwardMode.LOCAL) { "Local forwards do not have an ADB endpoint" }
        require(forward.reverseBindHost == PortForwardRule.LOOPBACK_HOST) {
            "ADB reverse forwarding must bind to 127.0.0.1"
        }
        require(port in 1..65535) { "ADB endpoint port is out of range" }
        requireSession().setPortForwardingR(
            forward.reverseBindHost,
            forward.reverseBindPort,
            address,
            port,
        )
        reverseForwardActive = true
        return forward.reverseBindPort
    }

    @Synchronized
    fun replaceReverseTarget(address: String, port: Int): Int {
        removeReverseForward()
        return addReverseForward(address, port)
    }

    @Synchronized
    fun removeForward() {
        val current = session ?: return
        if (forward.mode == ForwardMode.LOCAL) {
            runCatching { current.delPortForwardingL(forward.localPort) }
        } else {
            removeReverseForward()
        }
    }

    @Synchronized
    fun removeReverseForward() {
        val current = session ?: return
        if (reverseForwardActive) {
            runCatching { current.delPortForwardingR(forward.reverseBindPort) }
            reverseForwardActive = false
        }
    }

    @Synchronized
    fun disconnect() {
        val current = session
        session = null
        reverseForwardActive = false
        if (current != null) {
            if (forward.mode == ForwardMode.LOCAL) {
                runCatching { current.delPortForwardingL(forward.localPort) }
            } else {
                runCatching { current.delPortForwardingR(forward.reverseBindPort) }
            }
            current.disconnect()
        }
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

    private fun requireSession(): Session = session?.takeIf { it.isConnected }
        ?: error("SSH session is not connected")
}

class PinnedHostKeyRepository(
    expectedFingerprint: String,
    private val onObserved: (String) -> Unit = {},
) : HostKeyRepository {
    private val expected = normalize(expectedFingerprint)

    override fun check(host: String?, key: ByteArray): Int {
        val observed = fingerprint(key)
        onObserved(observed)
        return if (normalize(observed) == expected) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "pinned SHA-256 fingerprint"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    companion object {
        fun fingerprint(key: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            val encoded = Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
            return "SHA256:$encoded"
        }

        private fun normalize(value: String): String = value.trim()
            .removePrefix("SHA256:")
            .trimEnd('=')
    }
}
