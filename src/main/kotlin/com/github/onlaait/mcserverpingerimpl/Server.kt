package com.github.onlaait.mcserverpingerimpl

import com.github.onlaait.mcserverpinger.Log.logger
import com.github.onlaait.mcserverpinger.ServerPinger
import com.github.onlaait.mcserverpingerimpl.pingresult.PingFailResult
import com.github.onlaait.mcserverpingerimpl.pingresult.PingResult
import com.github.onlaait.mcserverpingerimpl.pingresult.PingSuccessResult
import com.github.onlaait.mcserverpingerimpl.pingresult.PingSuccessResult.Players
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.IOException
import java.net.UnknownHostException

class Server(val address: String) {

    private companion object {
        const val PING_COOLDOWN: Long = 8765

        const val SAMPLE_MAX_SIZE = 12
        val VALID_ONLINE_RANGE = 1..5000
        const val MODERATE_ONLINE = 50
        val USERNAME_RGX = Regex("^(§[\\da-fk-o])*\\w{3,16}(§[\\da-fk-o])*$")
        val WHITESPACES_RGX = Regex("\\s+")
    }

    private val pinger = ServerPinger(address)
    private val playersCache = mutableMapOf<String, Double>()

    var terminated = false
    var result: PingResult? = null
        private set

    init {
        Thread.ofVirtual().name("Server($address)").start {
            try {
                while (!terminated) {
                    Thread.ofVirtual().name("Server($address)-ping").start {
                        result = ping()
                    }
                    Thread.sleep(PING_COOLDOWN)
                }
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
                result = PingFailResult(address = address, error = e::class)
            }
        }
    }

    private fun ping(): PingResult? {
        val response =
            try {
                pinger.ping()
            } catch (e: IOException) {
                return if (e is UnknownHostException) {
                    PingFailResult(address = address, error = e::class)
                } else {
                    null
                }
            }

        val players = response.players
        val online = players.online
        val max = players.max
        val sample = players.sample
        val version = response.version.name
        val motd = response.description.let {
            if (it != null) {
                PlainTextComponentSerializer.plainText().serialize(it).replace(WHITESPACES_RGX, " ")
            } else {
                null
            }
        }

        val list =
            if (sample.isEmpty()) {
                emptyList()
            } else {
                val isOnlineValid = online in VALID_ONLINE_RANGE && sample.size <= online!!
                val pFail = 1.0 - sample.size / (if (isOnlineValid) online else MODERATE_ONLINE).toDouble()
                playersCache.entries.removeIf { e ->
                    e.setValue(e.value * pFail)
                    e.value < 0.01
                }
                val list = mutableListOf<String>()
                var nAnonymous = 0
                for (player in sample) {
                    val name = player.name
                    if (USERNAME_RGX.matches(name)) {
                        playersCache[name] = 1.0
                    } else {
                        if (name == "Anonymous Player") {
                            nAnonymous++
                        } else if (name.isNotBlank()) {
                            list += name
                        }
                    }
                }
                if (isOnlineValid) {
                    val excess = playersCache.size + nAnonymous - online
                    if (excess > 0) {
                        val oldPlayers = playersCache.filter { it.value != 1.0 }
                        for (r in 1..excess) {
                            val oldestPlayer = oldPlayers.minByOrNull { it.value } ?: break
                            playersCache -= oldestPlayer.key
                        }
                    }
                }
                list += playersCache.map { it.key }.sorted()
                list
            }

        return PingSuccessResult(
            address = address,
            players = Players(
                max = max,
                online = online,
                list = list
            ),
            version = version,
            motd = motd
        )
    }
}