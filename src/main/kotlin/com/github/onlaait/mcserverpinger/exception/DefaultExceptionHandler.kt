package com.github.onlaait.mcserverpinger.exception

import com.github.onlaait.mcserverpinger.Log
import kotlin.system.exitProcess

object DefaultExceptionHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        Log.logError(e)
        exitProcess(999)
    }
}