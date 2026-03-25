package com.github.onlaait.mcserverpinger.address

import com.github.onlaait.mcserverpinger.Log.logger
import org.slf4j.Logger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException

object AddressResolver {

    val LOGGER: Logger = logger

    fun resolve(address: ServerAddress): List<InetSocketAddress>? {
        try {
            val inetAddresses = InetAddress.getAllByName(address.address)
            return inetAddresses.map { InetSocketAddress(it, address.port) }
        } catch (_: UnknownHostException) {
            LOGGER.debug("Couldn't resolve server ${address.address} address")
            return null
        }
    }
}