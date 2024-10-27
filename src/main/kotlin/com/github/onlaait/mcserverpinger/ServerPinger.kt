package com.github.onlaait.mcserverpinger

import com.github.onlaait.mcserverpinger.address.AllowedAddressResolver
import com.github.onlaait.mcserverpinger.address.ServerAddress
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

class ServerPinger(address: String, var timeout: Int = 8000) {

    private companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val json =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
                allowTrailingComma = true
            }

        val rgxInvalid: Regex = Regex("\"[a-zA-Z0-9]*\": *}")
    }

    val address: ServerAddress = ServerAddress.parse(address)

    private var cachedInetSocketAddress: InetSocketAddress? = null

    fun ping(): StatusResponse {
        val addrs =
            sequence {
                val cached = cachedInetSocketAddress
                cached?.let { yield(it) }
                getInetSocketAddresses().forEach {
                    if (it != cached) yield(it)
                }
            }

        var res: String? = null
        lateinit var exception: Exception
        for (addr in addrs) {
            try {
                res = getResponse(addr)
                cachedInetSocketAddress = addr
                break
            } catch (e: IOException) {
                exception = e
            }
        }
        if (res == null) {
            cachedInetSocketAddress = null
            throw exception
        }

        try {
            val filteredStr = res.replace(rgxInvalid, "}")
            val jsonObject = json.parseToJsonElement(filteredStr) as? JsonObject
                ?: return StatusResponse(
                    description = null,
                    players = StatusResponse.Players(null, null, emptyList()),
                    version = StatusResponse.Version(null, null),
                    favicon = null
                )
            val description =
                jsonObject["description"].let {
                    when {
                        it is JsonObject -> {
                            if (it.size == 1) {
                                (it["text"] as? JsonPrimitive)?.contentOrNull?.let { text ->
                                    LegacyComponentSerializer.legacySection().deserialize(text)
                                }
                            } else {
                                JSONComponentSerializer.json().deserialize(it.toString())
                            }
                        }
                        it is JsonPrimitive && it !is JsonNull ->
                            LegacyComponentSerializer.legacySection().deserialize(it.content)
                        else -> null
                    }
                }
            val players =
                (jsonObject["players"] as? JsonObject).let {
                    val max = (it?.get("max") as? JsonPrimitive)?.intOrNull
                    val online = (it?.get("online") as? JsonPrimitive)?.intOrNull
                    val sample = mutableListOf<StatusResponse.Players.Player>()
                    (it?.get("sample") as? JsonArray)?.forEach { e ->
                        (e as? JsonObject)?.let { player ->
                            val id = (player["id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                            val name = (player["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                            sample += StatusResponse.Players.Player(id, name)
                        }
                    }
                    StatusResponse.Players(max, online, sample)
                }
            val version =
                (jsonObject["version"] as? JsonObject).let {
                    val name = (it?.get("name") as? JsonPrimitive)?.contentOrNull
                    val protocol = (it?.get("protocol") as? JsonPrimitive)?.intOrNull
                    StatusResponse.Version(name, protocol)
                }
            val favicon = (jsonObject["favicon"] as? JsonPrimitive)?.contentOrNull

            return StatusResponse(description, players, version, favicon)
        } catch (e: Exception) {
            throw RuntimeException("Invalid response: \"$res\"\nCaused by: ${e.stackTraceToString()}")
        }
    }

    private fun getInetSocketAddresses(): List<InetSocketAddress> =
        AllowedAddressResolver.resolve(address) ?: throw UnknownHostException()

    private fun getResponse(address: InetSocketAddress): String {
        val socket = Socket()
        socket.setSoTimeout(timeout)
        socket.connect(address, timeout)

        val dataOutputStream = DataOutputStream(socket.getOutputStream())
        val b = ByteArrayOutputStream()
        val handshake = DataOutputStream(b)
        handshake.writeByte(0x00) // packet id for handshake
        handshake.writeVarInt(765) // protocol version
        handshake.writeVarInt(address.hostString.length) // host length
        handshake.writeBytes(address.hostString) // host string
        handshake.writeShort(address.port) // port
        handshake.writeVarInt(1) // state (1 for handshake)
        dataOutputStream.writeVarInt(b.size()) // prepend size
        dataOutputStream.write(b.toByteArray()) // write handshake packet
        dataOutputStream.writeByte(0x01) // size is only 1
        dataOutputStream.writeByte(0x00) // packet id for ping

        val dataInputStream = DataInputStream(socket.getInputStream())
        dataInputStream.readVarInt() // size of packet
        val id = dataInputStream.readVarInt() // packet id
        if (id == -1) throw IOException("Premature end of stream.")
        if (id != 0x00) throw IOException("Invalid packetID") // we want a status response
        val length = dataInputStream.readVarInt() // length of json string
        if (length == -1) throw IOException("Premature end of stream.")
        if (length == 0) throw IOException("Invalid string length.")

        val input = ByteArray(length)
        dataInputStream.readFully(input) // read json string

        socket.close()

        return String(input)
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