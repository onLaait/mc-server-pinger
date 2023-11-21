package com.github.onlaait.mcserverpinger

import kotlin.system.exitProcess

object DefaultExceptionHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        Log.logError(e)
        exitProcess(999)
    }
}