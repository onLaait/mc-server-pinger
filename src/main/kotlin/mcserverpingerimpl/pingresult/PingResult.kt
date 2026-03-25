package com.github.onlaait.mcserverpingerimpl.pingresult

interface PingResult {

    val address: String

    fun encodeToString(): String
}