package com.github.onlaait.mcserverpinger

import br.com.azalim.mcserverping.MCPing
import br.com.azalim.mcserverping.MCPingOptions
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.IDN
import java.net.UnknownHostException
import java.nio.file.attribute.FileTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.io.path.*
import kotlin.math.min

val logger: Logger = LoggerFactory.getLogger("a")
val filePath = Path("servers.txt")
val pingers = mutableListOf<Thread>()
val terminal = Terminal(AnsiLevel.TRUECOLOR)
val print = sortedMapOf<Int, String>()
var onlineChanged = false
var changed = true
val usernameRegex = Regex("^(§[\\da-fk-o])*\\w{3,16}(§[\\da-fk-o])*\$")
const val weirdSpigotNum = 12 // 일부 Spigot 서버에서 인원이 최대 12명만 표시되는 현상
val lock = ReentrantLock()

fun main() {
    Thread.setDefaultUncaughtExceptionHandler(DefaultExceptionHandler)
    terminal.cursor.hide()
    if (!filePath.exists() || filePath.isDirectory()) filePath.createFile()
    thread(name = "Printer", isDaemon = true) {
        while (true) {
            lock.withLock {
                if (changed) {
                    val result = "\n" + print.map { it.value }.joinToString("\n") + "\n"
                    TerminalUtil.clearTerminal()
                    terminal.println(result)
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

val whitespacesRegex = Regex("\\s+")

fun pinger(n: Int, address: String) = thread(name = "Pinger$n($address)", isDaemon = true) {
    try {
        if (address.contains(' ')) throw PingerException("Address contains blank")
        val s = address.split(':')
        val port = if (s.size == 2) (s[1].toIntOrNull() ?: throw PingerException("Port is not integer")) else 25565
        val options = MCPingOptions.builder()
            .hostname(IDN.toASCII(s[0]))
            .port(port)
            .timeout(10000)
            .build()
        var latestOnline = -1
        val playersCache = sortedMapOf<String, Double>()
        while (true) {
            run {
                val response = try {
                    MCPing.getPing(options)
                } catch (e: IOException) {
                    if (Thread.interrupted()) return@thread
                    if (e is UnknownHostException) {
                        update(n, TextColors.brightRed("$address ${e.javaClass.name}"))
                    } else {
                        update(n, null)
                    }
                    return@run
                }
                if (Thread.interrupted()) return@thread
                val players = response.players
                val online = players?.online ?: -1
                val max = players?.max ?: -1
                val sample = players?.sample
                val version = response.version.name
                val motd = response.description.strippedText.replace(whitespacesRegex, " ")
                if (sample == null || (online != weirdSpigotNum && (online <= 12 || playersCache.size > online))) {
                    playersCache.clear()
                }
                sample?.map { it.name }?.filter { it.isNotBlank() && it != "Anonymous Player" }?.forEach { name ->
                    playersCache[name] = 1.0
                }
                val str = StringBuilder("$address ($online/$max) [$version] \"$motd\"")
                if (playersCache.isNotEmpty()) {
                    str.append("\n └ ${playersCache.map { it.key }.joinToString(", ")}")
                    val diff = online - playersCache.size
                    if (diff != 0) {
                        str.append(' ')
                        if (diff > 0) {
                            str.append('+')
                        }
                        str.append(diff)
                    }
                }
                if (latestOnline != online) onlineChanged = true
                latestOnline = online
                update(
                    n,
                    if (online >= 2) {
                        if (online >= 25) {
                            TextColors.brightCyan
                        } else {
                            TextColors.brightGreen
                        }
                    } else {
                        TextColors.white
                    }(str.toString())
                )

                // "00000000-0000-0000-0000-000000000000"
                val c = if (sample != null) {
                    sample.size.toDouble() / (if (online == weirdSpigotNum) min(30, max) else online) / 30 // min(n, max)의 n: 상식적으로 가능한 피크 인원
                } else {
                    0.0
                }
                for ((key, value) in playersCache.toMap()) {
                    if (!usernameRegex.matches(key)) {
                        playersCache.remove(key)
                        continue
                    }
                    playersCache[key] = value - c
                    if (value <= 0) playersCache.remove(key)
                }
            }
            Thread.sleep(2000)
        }
    } catch (e: InterruptedException) {
        return@thread
    } catch (t: Throwable) {
        update(n, TextColors.brightRed("$address ${t.javaClass.name}"))
        logger.error(t.stackTraceToString())
        return@thread
    }
}

fun update(n: Int, content: String?) {
    if (content == null) {
        if (print.remove(n) == null) return
    } else if (print.put(n, content) == content) {
        return
    }
    lock.withLock { changed = true }
}

class PingerException(message: String) : Exception(message)