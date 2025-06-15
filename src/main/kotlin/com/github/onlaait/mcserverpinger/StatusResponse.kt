package com.github.onlaait.mcserverpinger

import net.kyori.adventure.text.Component

data class StatusResponse(
    val description: Component?,
    val players: Players,
    val version: Version,
    val favicon: String?
) {

    data class Players(
        val max: Int?,
        val online: Int?,
        val sample: List<Player>
    ) {

        data class Player(
            val id: String,
            val name: String
        )
    }

    data class Version(
        val name: String?,
        val protocol: Int?
    )

    companion object {
        val EMPTY =
            StatusResponse(
                description = null,
                players = Players(null, null, emptyList()),
                version = Version(null, null),
                favicon = null
            )
    }
}