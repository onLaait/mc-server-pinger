package com.github.onlaait.mcserverpingerimpl

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.onlaait.mcserverpingerimpl.pingresult.PingResult
import java.nio.file.attribute.FileTime
import kotlin.io.path.*

private const val CONSOLE_COOLDOWN: Long = 1000

private val servers = mutableListOf<Server>()

fun main() {
    Thread.setDefaultUncaughtExceptionHandler(DefaultExceptionHandler)

    Thread.ofVirtual().name("Printer").start { terminal() }
    server()
}

private fun terminal() {
    val terminal = Terminal(AnsiLevel.TRUECOLOR)
    TerminalUtil.clearTerminal()
    terminal.cursor.hide()
    var priorResults: List<PingResult>? = null
    while (true) {
        val results = servers.mapNotNull { it.result }
        if (priorResults != results) {
            val str =
                if (servers.isEmpty()) {
                    "Server list is empty"
                } else {
                    buildString {
                        appendLine()
                        results.forEach {
                            appendLine(it.encodeToString())
                        }
                    }
                }
            TerminalUtil.clearTerminal()
            terminal.println(str)
            terminal.cursor.hide()
            priorResults = results
        }
        Thread.sleep(CONSOLE_COOLDOWN)
    }
}

private fun server() {
    val path = Path("servers.txt")
    if (!path.exists()) path.createFile()
    var priorModifiedTime: FileTime? = null
    while (true) {
        val lastModifiedTime = path.getLastModifiedTime()
        if (priorModifiedTime != lastModifiedTime) {
            val serverMap = servers.associateByTo(mutableMapOf()) { it.address }
            servers.clear()
            for (line in path.readLines()) {
                if (line.isBlank()) continue
                val address = line.trim().lowercase()
                servers += serverMap.getOrPut(address) { Server(address) }
            }
            for ((_, server) in serverMap) {
                if (!servers.contains(server)) server.terminated = true
            }
            priorModifiedTime = lastModifiedTime
        }
        Thread.sleep(500)
    }
}
