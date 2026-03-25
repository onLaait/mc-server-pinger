package com.github.onlaait.mcserverpinger.address

import com.github.onlaait.mcserverpinger.Log.logger
import org.slf4j.Logger
import java.util.*
import javax.naming.directory.InitialDirContext

fun interface RedirectResolver {

    fun lookupRedirect(var1: ServerAddress): ServerAddress?

    companion object {

        val LOGGER: Logger = logger

        val INVALID: RedirectResolver = RedirectResolver { null }

        fun createSrv(): RedirectResolver {
            val dirContext: InitialDirContext
            try {
                Class.forName("com.sun.jndi.dns.DnsContextFactory")
                val hashtable = Hashtable<String, String>()
                hashtable["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"
                hashtable["java.naming.provider.url"] = "dns:"
                hashtable["com.sun.jndi.dns.timeout.retries"] = "1"
                dirContext = InitialDirContext(hashtable)
            } catch (throwable: Throwable) {
                LOGGER.error("Failed to initialize SRV redirect resolved, some servers might not work", throwable)
                return INVALID
            }
            return RedirectResolver { address ->
                if (address.port == 25565) {
                    try {
                        val attributes = dirContext.getAttributes("_minecraft._tcp." + address.address, arrayOf("SRV"))
                        val attribute = attributes["srv"]
                        if (attribute != null) {
                            val strings = attribute.get().toString().split(' ', limit = 4)
                            return@RedirectResolver ServerAddress(strings[3], ServerAddress.portOrDefault(strings[2]))
                        }
                    } catch (_: Throwable) {
                    }
                }
                null
            }
        }
    }
}