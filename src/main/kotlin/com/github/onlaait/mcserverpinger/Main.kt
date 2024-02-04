package com.github.onlaait.mcserverpinger

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import com.github.onlaait.mcserverpinger.Log.logError
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.attribute.FileTime
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.io.path.*

val print = sortedMapOf<Int, Content>()
var changed = true
val lock = ReentrantLock()

fun main() {
    Thread.setDefaultUncaughtExceptionHandler(DefaultExceptionHandler)
    val filePath = Path("servers.txt")
    val pingers = mutableListOf<Thread>()
    val terminal = Terminal(AnsiLevel.TRUECOLOR)
    terminal.cursor.hide()
    if (!filePath.exists() || filePath.isDirectory()) filePath.createFile()
    thread(name = "Printer", isDaemon = true) {
        while (true) {
            lock.withLock {
                if (changed) {
                    val str = StringBuilder("\n")
                    for (content in print.toSortedMap().values) {
                        str.append(content.serialize())
                        str.append("\n")
                    }
                    TerminalUtil.clearTerminal()
                    terminal.println(str.toString())
                    terminal.cursor.hide()
                    changed = false
                }
            }
            Thread.sleep(500)
        }
    }
    var priorModifiedTime = FileTime.fromMillis(0)
    while (true) {
        val lastModifiedTime = filePath.getLastModifiedTime()
        if (priorModifiedTime != lastModifiedTime) {
            pingers.forEach { it.interrupt() }
            pingers.clear()
            print.clear()
            var n = 0
            for (line in filePath.reader().use { it.readLines() }) {
                if (line.isBlank()) continue
                pingers += pinger(n, line.trim())
                n++
            }
            if (n == 0) {
                println("${filePath.name} is empty")
                return
            }
            priorModifiedTime = lastModifiedTime
        }
        Thread.sleep(500)
    }
}

const val weirdSpigotNum = 12 // 일부 Spigot 서버에서 online이 최대 12명만 표시되는 현상
val whitespacesPattern: Pattern = Pattern.compile("\\s+")
val usernamePattern: Pattern = Pattern.compile("^(§[\\da-fk-o])*\\w{3,16}(§[\\da-fk-o])*\$")

fun pinger(n: Int, address: String) = thread(name = "Pinger$n($address)", isDaemon = true) {
    try {
        val pinger = ServerPinger(ServerAddress.parse(address))
        val playersCache = sortedMapOf<String, Double>()
        while (true) {
            run {
                val response = try {
                    pinger.ping()
                } catch (e: Exception) {
                    if (Thread.interrupted()) return@thread
                    update(
                        n,
                        if (e is IOException) {
                            if (e is UnknownHostException) Content(address = address, error = e) else null
                        } else {
                            logError(e)
                            Content(address = address, error = e)
                        }
                    )
                    return@run
                }
                if (Thread.interrupted()) return@thread

                val players = response.players
                val online = players.online ?: 0
                val max = players.max
                val sample = players.sample
                val motd = response.description.let { des ->
                    if (des != null) {
                        whitespacesPattern.matcher(PlainTextComponentSerializer.plainText().serialize(des)).replaceAll(" ")
                    } else {
                        null
                    }
                }
                val version = response.version.name

                val isOnlineNumWeird = (online == weirdSpigotNum)
                if (sample.isEmpty() || (!isOnlineNumWeird && (online <= 12 || playersCache.size > online))) {
                    playersCache.clear()
                }
                sample.map { it.name }.filter { it.isNotBlank() && it != "Anonymous Player" }.forEach { name ->
                    playersCache[name] = 1.0
                }

                val diff = online - playersCache.size
                val shouldAssumeOnline = (isOnlineNumWeird && diff < 0)
                val displayedOnline = if (shouldAssumeOnline) online-diff else online
                update(n, Content(
                    address = address,
                    players = Content.Players(
                        max = max,
                        online = displayedOnline,
                        onlineAssumed = shouldAssumeOnline,
                        list = playersCache.map { it.key }
                    ),
                    version = version,
                    motd = motd
                ))

                val c = if (sample.isNotEmpty()) {
                    sample.size.toDouble() / (if (online == weirdSpigotNum) (max ?: 20).coerceIn(weirdSpigotNum..30) else online) / 30
                } else {
                    0.0
                }
                for ((key, value) in playersCache.toMap()) {
                    if (!usernamePattern.matcher(key).matches()) {
                        playersCache.remove(key)
                        continue
                    }
                    playersCache[key] = value - c
                    if (value <= 0) playersCache.remove(key)
                }
            }
            Thread.sleep(2000)
        }
    } catch (_: InterruptedException) {
    } catch (t: Throwable) {
        update(n, Content(address = address, error = t))
        logError(t)
        return@thread
    }
}

fun update(n: Int, content: Content?) {
    if (content == null) {
        if (print.remove(n) == null) return
    } else if (print.put(n, content) == content) {
        return
    }
    lock.withLock { changed = true }
}

class PingerException(message: String) : Exception(message)

data class Content(
    val address: String,
    val players: Players = Players(),
    val version: String? = null,
    val motd: String? = null,
    val error: Throwable? = null
) {
    data class Players(
        val max: Int? = null,
        val online: Int? = null,
        val onlineAssumed: Boolean = false,
        val list: List<String> = listOf()
    )

    fun serialize(): String {
        if (error != null) return TextColors.brightRed("$address ${error.javaClass.name}")
        val str = StringBuilder("$address (")
        if (players.online != null && players.max != null) {
            str.append(players.online)
            if (players.onlineAssumed) str.append('*')
            str.append("/${players.max}")
        } else {
            str.append("???")
        }
        str.append(')')
        if (version != null) {
            str.append(" [$version]")
        }
        if (motd != null) {
            str.append(" \"$motd\"")
        }
        if (players.list.isNotEmpty()) {
            str.append("\n └ ${players.list.joinToString(", ")}")
            if (players.online != null) {
                val diff = players.online - players.list.size
                if (diff != 0) {
                    str.append(' ')
                    if (diff > 0) {
                        str.append('+')
                    }
                    str.append(diff)
                }
            }
        }
        return if (players.online != null && players.online >= 2) {
            if (players.online >= 25) {
                TextColors.brightCyan
            } else {
                TextColors.brightGreen
            }
        } else {
            TextColors.white
        }(str.toString())
    }
}

