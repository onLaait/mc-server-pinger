package com.github.onlaait.mcserverpingerimpl

import com.github.onlaait.mcserverpinger.Log.logger
import kotlin.system.exitProcess

object DefaultExceptionHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stackTrace = e.stackTraceToString()
        logger.error(stackTrace)
        TerminalUtil.clearTerminal()
        println(stackTrace)
        exitProcess(1)
    }
}