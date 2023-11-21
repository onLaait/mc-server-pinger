package com.github.onlaait.mcserverpinger

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Log {

    val logger: Logger = LoggerFactory.getLogger("mc-server-pinger")

    fun logError(t: Throwable) = logger.error(t.stackTraceToString())
}