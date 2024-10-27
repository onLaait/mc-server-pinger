package com.github.onlaait.mcserverpinger.address

import com.github.onlaait.mcserverpinger.PingerException
import com.google.common.net.HostAndPort

import java.net.IDN

class ServerAddress {

    constructor(host: String, port: Int) : this(HostAndPort.fromParts(host, port))

    private constructor(hostAndPort: HostAndPort) {
        this.hostAndPort = hostAndPort
    }

    companion object {
        fun parse(address: String): ServerAddress =
            try {
                val hostAndPort = HostAndPort.fromString(address).withDefaultPort(25565)
                if (hostAndPort.host.isEmpty()) throw PingerException("Host is empty")
                ServerAddress(hostAndPort)
            } catch (_: IllegalArgumentException) {
                throw PingerException("Failed to parse URL $address")
            }

        fun portOrDefault(port: String): Int =
            try {
                port.trim().toInt()
            } catch (_: Exception) {
                25565
            }
    }

    private val hostAndPort: HostAndPort

    val address: String
        get() =
            try {
                IDN.toASCII(hostAndPort.host)
            } catch (_: IllegalArgumentException) {
                ""
            }

    val port: Int
        get() = hostAndPort.port

    override fun toString(): String = hostAndPort.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ServerAddress) return hostAndPort == other.hostAndPort
        return false
    }

    override fun hashCode(): Int = hostAndPort.hashCode()
}