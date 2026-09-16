package com.yayo.sshtunneling.model

/** Pure validation used by both persistence and import flows. */
object TunnelDataValidation {
    fun errors(data: TunnelAppData): List<String> = buildList {
        val hostIds = mutableSetOf<String>()
        data.hosts.forEachIndexed { index, host ->
            if (host.id.isBlank()) add("hosts[$index].id is required")
            if (!hostIds.add(host.id)) add("duplicate host id: ${host.id}")
            if (host.port !in 1..65535) add("hosts[$index].port is out of range")
            if (host.keepAliveSeconds !in 1..86_400) add("hosts[$index].keep_alive is out of range")
        }

        val forwardIds = mutableSetOf<String>()
        data.forwards.forEachIndexed { index, forward ->
            if (forward.id.isBlank()) add("forwards[$index].id is required")
            if (!forwardIds.add(forward.id)) add("duplicate forward id: ${forward.id}")
            if (forward.hostId !in hostIds) add("forwards[$index].host_id does not exist")
            if (forward.localPort !in 1..65535) add("forwards[$index].local_port is out of range")
            if (forward.remotePort !in 1..65535) add("forwards[$index].remote_port is out of range")
            if (forward.mode != ForwardMode.LOCAL) {
                if (forward.reverseBindHost != PortForwardRule.LOOPBACK_HOST) {
                    add("forwards[$index].reverse_bind_host must be 127.0.0.1")
                }
                if (forward.reverseBindPort !in 1..65535) {
                    add("forwards[$index].reverse_bind_port is out of range")
                }
            }
            if (forward.widgetSlot != null && forward.widgetSlot !in 0 until WidgetSlots.COUNT) {
                add("forwards[$index].widget_slot is out of range")
            }
        }
    }

    fun requireValid(data: TunnelAppData): TunnelAppData {
        val errors = errors(data)
        require(errors.isEmpty()) { "Invalid tunnel settings: ${errors.joinToString()}" }
        return data
    }
}
