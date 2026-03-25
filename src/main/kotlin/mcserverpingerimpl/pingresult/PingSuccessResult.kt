package com.github.onlaait.mcserverpingerimpl.pingresult

import com.github.ajalt.mordant.rendering.TextColors

data class PingSuccessResult(
    override val address: String,
    val players: Players = Players.EMPTY,
    val version: String? = null,
    val motd: String? = null
) : PingResult {

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

    override fun encodeToString(): String {
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