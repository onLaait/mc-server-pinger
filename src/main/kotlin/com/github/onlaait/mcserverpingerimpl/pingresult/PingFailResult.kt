package com.github.onlaait.mcserverpingerimpl.pingresult

import com.github.ajalt.mordant.rendering.TextColors
import kotlin.reflect.KClass

data class PingFailResult(
    override val address: String,
    val error: KClass<out Exception>
) : PingResult {

    override fun encodeToString(): String =
        TextColors.brightRed("$address ${error.qualifiedName}")
}