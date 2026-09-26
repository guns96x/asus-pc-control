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

    suspend fun sendMagicPacket(macStr: String, port: Int = 9): Result<String> = withContext(Dispatchers.IO) {
        try {
            val macBytes = parseMac(macStr)
            val packetData = ByteArray(6 + 16 * macBytes.size)

            for (i in 0 until 6) {
                packetData[i] = 0xFF.toByte()
            }
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packetData, 6 + i * macBytes.size, macBytes.size)
            }

            var sentCount = 0
            val socket = DatagramSocket()
            socket.broadcast = true

            // 1. Global broadcast
            try {
                val globalPacket = DatagramPacket(packetData, packetData.size, InetAddress.getByName("255.255.255.255"), port)
                socket.send(globalPacket)
                sentCount++
            } catch (e: Exception) {
                Log.w(TAG, "Global broadcast failed: ${e.message}")
            }

            // 2. All interface broadcast addresses
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        val broadcast = interfaceAddress.broadcast
                        if (broadcast != null) {
                            val ifPacket = DatagramPacket(packetData, packetData.size, broadcast, port)
                            socket.send(ifPacket)
                            sentCount++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Subnet broadcast failed: ${e.message}")
            } finally {
                socket.close()
            }

            Log.i(TAG, "WOL Magic packet sent to $macStr via $sentCount broadcast routes")
            Result.success("WOL пакет відправлено ($sentCount маршрутів)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WOL packet", e)
            Result.failure(e)
        }
    }

    private fun parseMac(macStr: String): ByteArray {
        val clean = macStr.replace(":", "").replace("-", "").trim()
        require(clean.length == 12) { "Невірний формат MAC адреси: $macStr" }

        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }
}
