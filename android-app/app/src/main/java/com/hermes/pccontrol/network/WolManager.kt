package com.hermes.pccontrol.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

object WolManager {
    private const val TAG = "WolManager"

    private fun safeLogInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            // Ignored in non-Android JVM environments
        }
    }

    private fun safeLogWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            // Ignored in non-Android JVM environments
        }
    }

    private fun safeLogError(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        } catch (_: Throwable) {
            // Ignored in non-Android JVM environments
        }
    }

    fun parseMac(macStr: String): ByteArray {
        val clean = macStr.replace(":", "").replace("-", "").replace(".", "").trim()
        require(clean.length == 12 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Невірний формат MAC-адреси: $macStr"
        }

        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }

    fun validatePort(port: Int): Int {
        require(port in 1..65535) { "Невірний порт для WOL: $port (має бути 1-65535)" }
        return port
    }

    fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        require(macBytes.size == 6) { "MAC-адреса має містити рівно 6 байтів" }
        val packetData = ByteArray(6 + 16 * macBytes.size)

        for (i in 0 until 6) {
            packetData[i] = 0xFF.toByte()
        }
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packetData, 6 + i * macBytes.size, macBytes.size)
        }
        return packetData
    }

    internal fun getAvailableBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        try {
            addresses.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
            safeLogWarn("Global broadcast resolution failed: ${e.message}")
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        val broadcast = interfaceAddress.broadcast
                        if (broadcast != null && !addresses.contains(broadcast)) {
                            addresses.add(broadcast)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            safeLogWarn("Subnet broadcast discovery failed: ${e.message}")
        }

        return addresses
    }

    internal fun sendPacketsToAddresses(
        packetData: ByteArray,
        port: Int,
        addresses: List<InetAddress>
    ): Int {
        if (addresses.isEmpty()) return 0
        var sentCount = 0

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (address in addresses) {
                    try {
                        val packet = DatagramPacket(packetData, packetData.size, address, port)
                        socket.send(packet)
                        sentCount++
                    } catch (e: Exception) {
                        safeLogWarn("Failed to send WOL packet to $address: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            safeLogWarn("Failed to open broadcast socket: ${e.message}")
        }

        return sentCount
    }

    internal fun sendMagicPacketInternal(
        macStr: String,
        port: Int = 9,
        broadcastAddressesProvider: () -> List<InetAddress> = { getAvailableBroadcastAddresses() },
        packetSender: (packetData: ByteArray, port: Int, addresses: List<InetAddress>) -> Int = { data, p, addrs ->
            sendPacketsToAddresses(data, p, addrs)
        }
    ): Result<String> {
        return try {
            validatePort(port)
            val macBytes = parseMac(macStr)
            val packetData = buildMagicPacket(macBytes)
            val addresses = broadcastAddressesProvider()

            val sentCount = packetSender(packetData, port, addresses)

            if (sentCount == 0) {
                Result.failure(Exception("Не вдалося відправити WOL-пакет: жоден широкомовний маршрут недоступний"))
            } else {
                safeLogInfo("WOL Magic packet sent to $macStr via $sentCount broadcast routes")
                Result.success("WOL пакет відправлено ($sentCount маршрутів)")
            }
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            safeLogError("Failed to send WOL packet", e)
            Result.failure(e)
        }
    }

    suspend fun sendMagicPacket(macStr: String, port: Int = 9): Result<String> = withContext(Dispatchers.IO) {
        sendMagicPacketInternal(macStr, port)
    }
}
