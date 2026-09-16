package com.yayo.sshtunneling.adb

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.ArrayDeque
import java.util.EnumMap

/** Lifecycle-aware mDNS discovery shared by all ADB tunnel sessions in the service. */
class AdbEndpointDiscovery(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val _snapshot = MutableStateFlow(AdbDiscoverySnapshot())
    private val listeners = EnumMap<AdbServiceKind, NsdManager.DiscoveryListener>(AdbServiceKind::class.java)
    private val endpoints = mutableMapOf<String, AdbEndpoint>()
    private val resolveQueue = ArrayDeque<Pair<AdbServiceKind, NsdServiceInfo>>()
    private var resolveInProgress = false
    private var networkCallbackRegistered = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private var probeJob: Job? = null
    private val localAddresses = mutableMapOf<Network, Set<InetAddress>>()

    val snapshot: StateFlow<AdbDiscoverySnapshot> = _snapshot.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkAddresses(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            localAddresses[network] = linkProperties.linkAddresses.map { it.address }.toSet()
            refreshSelfClassifications()
        }

        override fun onLost(network: Network) {
            localAddresses.remove(network)
            refreshSelfClassifications()
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (listeners.isNotEmpty()) return
        if (Build.VERSION.SDK_INT >= 37 &&
            appContext.checkSelfPermission(LOCAL_NETWORK_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _snapshot.value = _snapshot.value.copy(errorMessage = "로컬 네트워크 권한이 필요합니다.")
            return
        }

        try {
            registerNetworkCallback()
            multicastLock = wifiManager?.createMulticastLock("ssh-tunneling-adb").apply {
                this?.setReferenceCounted(false)
                this?.acquire()
            }
            AdbServiceKind.entries.forEach { kind ->
                val listener = listenerFor(kind)
                listeners[kind] = listener
                nsdManager.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }
            _snapshot.value = AdbDiscoverySnapshot(isRunning = true)
        } catch (securityException: SecurityException) {
            stop()
            _snapshot.value = AdbDiscoverySnapshot(errorMessage = "로컬 네트워크 권한이 거부되었습니다.")
        } catch (failure: RuntimeException) {
            stop()
            _snapshot.value = AdbDiscoverySnapshot(errorMessage = failure.message ?: "ADB 검색을 시작할 수 없습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        listeners.values.forEach { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        listeners.clear()
        resolveQueue.clear()
        resolveInProgress = false
        probeJob?.cancel()
        probeJob = null
        endpoints.clear()
        if (networkCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        localAddresses.clear()
        multicastLock?.let { lock -> if (lock.isHeld) lock.release() }
        multicastLock = null
        _snapshot.value = AdbDiscoverySnapshot()
    }

    fun close() = stop()

    suspend fun awaitEndpoint(kind: AdbServiceKind, timeoutMillis: Long = 120_000): AdbEndpoint? =
        withTimeoutOrNull(timeoutMillis) {
            snapshot.first { state ->
                state.errorMessage != null || AdbEndpointSelector.select(state.endpoints, kind) != null
            }.let { state -> AdbEndpointSelector.select(state.endpoints, kind) }
        }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        networkCallbackRegistered = true
    }

    private fun updateNetworkAddresses(network: Network) {
        connectivityManager.getLinkProperties(network)?.let { properties ->
            localAddresses[network] = properties.linkAddresses.map { it.address }.toSet()
            refreshSelfClassifications()
        }
    }

    private fun listenerFor(kind: AdbServiceKind) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (resolveQueue.none { it.first == kind && it.second.serviceName == serviceInfo.serviceName }) {
                resolveQueue += kind to serviceInfo
            }
            resolveNext()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            endpoints.remove(key(kind, serviceInfo.serviceName))
            publish()
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            listeners.remove(kind)
            publish(error = "${kind.name} 검색을 시작하지 못했습니다 ($errorCode).")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolveInProgress || resolveQueue.isEmpty()) return
        resolveInProgress = true
        val (kind, unresolved) = resolveQueue.removeFirst()
        nsdManager.resolveService(unresolved, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolveInProgress = false
                resolveNext()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val addresses = if (Build.VERSION.SDK_INT >= 34) {
                    serviceInfo.hostAddresses
                } else {
                    listOfNotNull(serviceInfo.host)
                }
                val network = if (Build.VERSION.SDK_INT >= 34) serviceInfo.network else null
                val self = AddressMatcher.isSelf(addresses, localAddresses(network))
                val endpoint = AdbEndpoint(
                    kind = kind,
                    instanceName = serviceInfo.serviceName,
                    addresses = addresses,
                    port = serviceInfo.port,
                    network = network,
                    isSelf = self,
                )
                endpoints[key(kind, serviceInfo.serviceName)] = endpoint
                publish()
                if (self) probe(endpoint)
                resolveInProgress = false
                resolveNext()
            }
        })
    }

    private fun probe(endpoint: AdbEndpoint) {
        val address = endpoint.primaryAddress ?: return
        if (endpoint.port !in 1..65535) return
        probeJob?.cancel()
        probeJob = scope.launch(Dispatchers.IO) {
            val reachable = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, endpoint.port), TCP_TIMEOUT_MS)
                }
            }.isSuccess
            withContext(Dispatchers.Main.immediate) {
                val key = key(endpoint.kind, endpoint.instanceName)
                val current = endpoints[key]
                if (current != null && current.port == endpoint.port && current.addresses == endpoint.addresses) {
                    endpoints[key] = current.copy(tcpReachable = reachable)
                    publish()
                }
            }
        }
    }

    private fun refreshSelfClassifications() {
        endpoints.entries.toList().forEach { (key, endpoint) ->
            val self = AddressMatcher.isSelf(endpoint.addresses, localAddresses(endpoint.network))
            if (self != endpoint.isSelf) {
                val updated = endpoint.copy(isSelf = self, tcpReachable = if (self) null else null)
                endpoints[key] = updated
                if (self) probe(updated)
            }
        }
        publish()
    }

    private fun localAddresses(network: Network?): Set<InetAddress> =
        network?.let { localAddresses[it] } ?: localAddresses.values.flatten().toSet()

    private fun publish(error: String? = _snapshot.value.errorMessage) {
        _snapshot.value = AdbDiscoverySnapshot(
            endpoints = endpoints.values.sortedWith(compareBy({ it.kind.ordinal }, { it.instanceName })).toList(),
            isRunning = listeners.isNotEmpty(),
            errorMessage = error,
        )
    }

    private fun key(kind: AdbServiceKind, name: String) = "${kind.name}:$name"

    companion object {
        private const val TCP_TIMEOUT_MS = 2_000
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
