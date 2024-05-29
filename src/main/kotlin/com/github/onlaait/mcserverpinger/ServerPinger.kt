package com.github.onlaait.mcserverpinger

import com.github.onlaait.mcserverpinger.Log.logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.net.UnknownHostException

class ServerPinger(address: String) {

    private companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val json =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
                allowTrailingComma = true
            }

        val rgxInvalid = Regex("\"[a-zA-Z0-9]*\": *}")
    }

    val address: ServerAddress = ServerAddress.parse(address)

    var timeout = 10000

    fun ping(): StatusResponse {
        val optional = AllowedAddressResolver.DEFAULT.resolve(address).map(Address::getInetSocketAddress)
        if (optional.isEmpty) throw UnknownHostException()
        val inetSocketAddress = optional.get()

        val socket = Socket()
        socket.setSoTimeout(timeout)
        socket.connect(inetSocketAddress, timeout)

        val dataOutputStream = DataOutputStream(socket.getOutputStream())
        val b = ByteArrayOutputStream()
        val handshake = DataOutputStream(b)
        handshake.writeByte(0x00) // packet id for handshake
        handshake.writeVarInt(999) // protocol version
        handshake.writeVarInt(inetSocketAddress.hostString.length) // host length
        handshake.writeBytes(inetSocketAddress.hostString) // host string
        handshake.writeShort(inetSocketAddress.port) // port
        handshake.writeVarInt(1) // state (1 for handshake)
        dataOutputStream.writeVarInt(b.size()) // prepend size
        dataOutputStream.write(b.toByteArray()) // write handshake packet
        dataOutputStream.writeByte(0x01) // size is only 1
        dataOutputStream.writeByte(0x00) // packet id for ping

        val dataInputStream = DataInputStream(socket.getInputStream())
        dataInputStream.readVarInt() // size of packet
        val id = dataInputStream.readVarInt() // packet id
        if (id == -1) {
            throw IOException("Premature end of stream.")
        }
        if (id != 0x00) { // we want a status response
            throw IOException("Invalid packetID")
        }
        val length = dataInputStream.readVarInt() // length of json string
        if (length == -1) {
            throw IOException("Premature end of stream.")
        }
        if (length == 0) {
            throw IOException("Invalid string length.")
        }

        val input = ByteArray(length)
        dataInputStream.readFully(input) // read json string

        socket.close()

        val str = String(input)
        try {
            val filteredStr = str.replace(rgxInvalid, "}")
            val jsonObject = json.parseToJsonElement(filteredStr).jsonObject
            val jsonDescription = jsonObject["description"]
            val description = when {
                jsonDescription is JsonObject -> {
                    if (jsonDescription.keys.size == 1) {
                        try {
                            LegacyComponentSerializer.legacySection().deserialize(jsonDescription.jsonObject["text"]!!.jsonPrimitive.content)
                        } catch (e: NullPointerException) {
                            null
                        }
                    } else {
                        JSONComponentSerializer.json().deserialize(jsonDescription.jsonObject.toString())
                    }
                }
                jsonDescription != null -> {
                    LegacyComponentSerializer.legacySection().deserialize(jsonDescription.jsonPrimitive.content)
                }
                else -> {
                    null
                }
            }
            val players = jsonObject["players"]?.jsonObject.let { players ->
                val max = players?.get("max")?.jsonPrimitive?.int
                val online = players?.get("online")?.jsonPrimitive?.int
                val sample = mutableListOf<StatusResponse.Players.Player>()
                players?.get("sample")?.jsonArray?.forEach {
                    it.jsonObject.let { player ->
                        try {
                            sample += StatusResponse.Players.Player(
                                player["id"]!!.jsonPrimitive.content,
                                player["name"]!!.jsonPrimitive.content
                            )
                        } catch (_: NullPointerException) {
                        }
                    }
                }
                StatusResponse.Players(max, online, sample)
            }
            val version = jsonObject["version"]?.jsonObject.let { version ->
                val name = version?.get("name")?.jsonPrimitive?.content
                val protocol = version?.get("protocol")?.jsonPrimitive?.int
                StatusResponse.Version(name, protocol)
            }
            val favicon = jsonObject["favicon"]?.jsonPrimitive?.content

            return StatusResponse(description, players, version, favicon)
        } catch (e: Exception) {
            logger.error("Invalid response: $str")
            throw RuntimeException("Invalid response: \"$str\"\nCaused by: ${e.stackTraceToString()}")
        }
    }

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
    }

    private fun DataInputStream.readVarInt(): Int {
        var i = 0
        var j = 0
        while (true) {
            val k = this.readUnsignedByte()
            i = i or (k and 0x7F shl j++ * 7)
            if (j > 5) throw IOException("VarInt too big")
            if (k and 0x80 != 128) break
        }
        return i
    }

    private fun DataOutputStream.writeVarInt(v: Int) {
        var v = v
        while (true) {
            if (v and -0x80 == 0) {
                this.writeByte(v)
                return
            }
            this.writeByte(v and 0x7F or 0x80)
            v = v ushr 7
        }
    }
}