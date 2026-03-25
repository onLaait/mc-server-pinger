package com.github.onlaait.mcserverpinger.address

import java.net.InetSocketAddress

object AllowedAddressResolver {

    private val redirectResolver: RedirectResolver = RedirectResolver.createSrv()

    fun resolve(address: ServerAddress): List<InetSocketAddress>? =
        AddressResolver.resolve(redirectResolver.lookupRedirect(address) ?: address)
}