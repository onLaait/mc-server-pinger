package com.github.onlaait.mcserverpinger

import kotlinx.serialization.json.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.io.*
import java.net.Socket
import java.net.UnknownHostException

class ServerPinger() {

    constructor(address: ServerAddress) : this() {
        this.address = address
    }

    lateinit var address: ServerAddress
    var timeout = 5000

    private fun DataInputStream.readVarInt(): Int {
        var i = 0
        var j = 0
        while (true) {
            val k = this.readUnsignedByte()
            i = i or (k and 0x7F shl j++ * 7)
            if (j > 5) throw RuntimeException("VarInt too big")
            if (k and 0x80 != 128) break
        }
        return i
    }

    private fun DataOutputStream.writeVarInt(paramInt: Int) {
        var paramInt = paramInt
        while (true) {
            if (paramInt and -0x80 == 0) {
                this.writeByte(paramInt)
                return
            }
            this.writeByte(paramInt and 0x7F or 0x80)
            paramInt = paramInt ushr 7
        }
    }

    fun ping(): StatusResponse {
        val optional = AllowedAddressResolver.DEFAULT.resolve(address).map(Address::getInetSocketAddress)
        if (optional.isEmpty) throw UnknownHostException()
        val inetSocketAddress = optional.get()

        val socket = Socket()
        socket.setSoTimeout(timeout)
        socket.connect(inetSocketAddress, timeout)
        val outputStream = socket.getOutputStream()
        val dataOutputStream = DataOutputStream(outputStream)
        val inputStream = socket.getInputStream()
        val inputStreamReader = InputStreamReader(inputStream)
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
        val dataInputStream = DataInputStream(inputStream)
        val size = dataInputStream.readVarInt() // size of packet
        var id = dataInputStream.readVarInt() // packet id
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
        val json = String(input)
        val now = System.currentTimeMillis()
        dataOutputStream.writeByte(0x09) // size of packet
        dataOutputStream.writeByte(0x01) // 0x01 for ping
        dataOutputStream.writeLong(now) // time!?
        dataInputStream.readVarInt()
        id = dataInputStream.readVarInt()

        dataOutputStream.close()
        outputStream.close()
        inputStreamReader.close()
        inputStream.close()
        socket.close()

        if (id == -1) {
            throw IOException("Premature end of stream.")
        }
        if (id != 0x01) {
            throw IOException("Invalid packetID")
        }

        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val jsonDescription = jsonObject["description"]
        val description = when {
            jsonDescription is JsonObject -> {
                JSONComponentSerializer.json().deserialize(jsonDescription.jsonObject.toString())
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
            val sample = players?.get("sample")?.jsonArray?.map {
                it.jsonObject.let { player ->
                    StatusResponse.Players.Player(
                        player["id"]!!.jsonPrimitive.content,
                        player["name"]!!.jsonPrimitive.content
                    )
                }
            } ?: listOf()
            StatusResponse.Players(max, online, sample)
        }
        val version = jsonObject["version"]?.jsonObject.let { version ->
            val name = version?.get("name")?.jsonPrimitive?.content
            val protocol = version?.get("protocol")?.jsonPrimitive?.int
            StatusResponse.Version(name, protocol)
        }
        val favicon = jsonObject["favicon"]?.jsonPrimitive?.content

        return StatusResponse(description, players, version, favicon)
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
}