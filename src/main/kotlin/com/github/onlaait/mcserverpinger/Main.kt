package com.github.onlaait.mcserverpinger

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import com.github.onlaait.mcserverpinger.Log.logError
import com.github.onlaait.mcserverpinger.exception.DefaultExceptionHandler
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.attribute.FileTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.io.path.*
import kotlin.reflect.KClass

private const val CONSOLE_COOLDOWN: Long = 500
private const val PING_COOLDOWN: Long = 8765

private val print = sortedMapOf<Int, PingResult>()
private var changed = true
private val lock = ReentrantLock()

fun main() {
    Thread.setDefaultUncaughtExceptionHandler(DefaultExceptionHandler)

    thread(name = "Printer", isDaemon = true) {
        val terminal = Terminal(AnsiLevel.TRUECOLOR)
        terminal.cursor.hide()
        while (true) {
            lock.withLock {
                if (changed) {
                    val str =
                        buildString {
                            appendLine()
                            for ((_, content) in print) {
                                appendLine(content.encodeToString())
                            }
                        }
                    TerminalUtil.clearTerminal()
                    terminal.println(str)
                    terminal.cursor.hide()
                    changed = false
                }
            }
            Thread.sleep(CONSOLE_COOLDOWN)
        }
    }

    val path = Path("servers.txt")
    if (!path.exists()) path.createFile()
    val pingers = mutableListOf<Thread>()
    var priorModifiedTime: FileTime? = null
    while (true) {
        val lastModifiedTime = path.getLastModifiedTime()
        if (priorModifiedTime != lastModifiedTime) {
            pingers.forEach { it.interrupt() }
            pingers.clear()
            lock.withLock {
                print.clear()
            }
            var n = 0
            for (line in path.readLines()) {
                if (line.isBlank()) continue
                pingers += pinger(n, line.trim())
                n++
            }
            if (n == 0) {
                println("${path.name} is empty")
                return
            }
            priorModifiedTime = lastModifiedTime
        }
        Thread.sleep(500)
    }
}

private const val WEIRD_SPIGOT_NUM = 12 // 일부 Spigot 서버에서 online이 최대 12까지만 표시되는 현상
private val RGX_WHITESPACES = Regex("\\s+")
private val RGX_USERNAME = Regex("^(§[\\da-fk-o])*\\w{3,16}(§[\\da-fk-o])*$")

private fun pinger(n: Int, address: String): Thread = Thread.ofVirtual().name("Pinger$n($address)").start thread@ {
    try {
        val pinger = ServerPinger(address)
        val playersCache = sortedMapOf<String, Double>()
        while (true) {
            run {
                val response =
                    try {
                        pinger.ping()
                    } catch (e: Exception) {
                        if (Thread.interrupted()) return@thread
                        update(
                            n,
                            if (e is IOException) {
                                if (e is UnknownHostException) PingResult(address = address, error = e::class) else null
                            } else {
                                logError(e)
                                PingResult(address = address, error = e::class)
                            }
                        )
                        return@run
                    }
                if (Thread.interrupted()) return@thread

                val players = response.players
                val online = players.online
                val max = players.max
                val sample = players.sample
                val motd = response.description.let {
                    if (it != null) {
                        PlainTextComponentSerializer.plainText().serialize(it).replace(RGX_WHITESPACES, " ")
                    } else {
                        null
                    }
                }
                val version = response.version.name
                val isOnlineWeird = online == WEIRD_SPIGOT_NUM

                if (sample.isEmpty() || (!isOnlineWeird && online != null && (online <= 12 || playersCache.size > online))) {
                    playersCache.clear()
                }
                sample.map { it.name }.filter { it.isNotBlank() && it != "Anonymous Player" }.forEach { name ->
                    playersCache[name] = 1.0
                }

                val shouldAssumeOnline: Boolean
                val displayedOnline: Int?
                if (online != null) {
                    val diff = online - playersCache.size
                    shouldAssumeOnline = (isOnlineWeird && diff < 0)
                    displayedOnline = if (shouldAssumeOnline) online - diff else online
                } else {
                    shouldAssumeOnline = false
                    displayedOnline = null
                }
                update(n, PingResult(
                    address = address,
                    players = PingResult.Players(
                        max = max,
                        online = displayedOnline,
                        onlineAssumed = shouldAssumeOnline,
                        list = playersCache.map { it.key }
                    ),
                    version = version,
                    motd = motd
                ))

                val c =
                    if (sample.isNotEmpty()) {
                        sample.size.toDouble() / (if (online == WEIRD_SPIGOT_NUM) (max ?: 20).coerceIn(WEIRD_SPIGOT_NUM..30) else online ?: sample.size) / 30
                    } else {
                        0.0
                    }
                for ((key, value) in playersCache.toMap()) {
                    if (!RGX_USERNAME.matches(key)) {
                        playersCache -= key
                        continue
                    }
                    playersCache[key] = value - c
                    if (value <= 0) playersCache -= key
                }
            }
            Thread.sleep(PING_COOLDOWN)
        }
    } catch (_: InterruptedException) {
    } catch (t: Throwable) {
        update(n, PingResult(address = address, error = t::class))
        logError(t)
        return@thread
    }
}

private fun update(n: Int, content: PingResult?) {
    lock.withLock {
        if (content == null) {
            if (print.remove(n) == null) return
        } else if (print.put(n, content) == content) {
            return
        }
        changed = true
    }
}

private data class PingResult(
    val address: String,
    val players: Players = Players.EMPTY,
    val version: String? = null,
    val motd: String? = null,
    val error: KClass<out Throwable>? = null
) {

    data class Players(
        val max: Int? = null,
        val online: Int? = null,
        val onlineAssumed: Boolean = false,
        val list: List<String> = emptyList()
    ) {
        companion object {
            val EMPTY = Players()
        }
    }

    fun encodeToString(): String {
        if (error != null) return TextColors.brightRed("$address ${error.qualifiedName}")

        val str = buildString {
            append("$address (")
            if (players.online != null && players.max != null) {
                append(players.online)
                if (players.onlineAssumed) append('*')
                append("/${players.max}")
            } else {
                append("???")
            }
            append(')')
            if (version != null) append(" [$version]")
            if (motd != null) append(" \"$motd\"")
            if (players.list.isNotEmpty()) {
                append("\n └ ${players.list.joinToString(", ")}")
                if (players.online != null) {
                    val diff = players.online - players.list.size
                    if (diff != 0) {
                        append(' ')
                        if (diff > 0) append('+')
                        append(diff)
                    }
                }
            }
        }

        val online = players.online ?: players.list.size
        val color =
            if (online >= 2) {
                if (online >= 30) {
                    TextColors.brightCyan
                } else {
                    TextColors.brightGreen
                }
            } else {
                TextColors.white
            }

        return color(str)
    }
}

