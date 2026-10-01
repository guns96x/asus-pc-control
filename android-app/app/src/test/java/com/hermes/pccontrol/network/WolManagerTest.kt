package com.hermes.pccontrol.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class WolManagerTest {

    @Test
    fun testParseMac_validFormats() {
        val expected = byteArrayOf(
            0x12.toByte(), 0x34.toByte(), 0x56.toByte(),
            0x78.toByte(), 0x9A.toByte(), 0xBC.toByte()
        )

        assertArrayEquals(expected, WolManager.parseMac("12:34:56:78:9A:BC"))
        assertArrayEquals(expected, WolManager.parseMac("12-34-56-78-9a-bc"))
        assertArrayEquals(expected, WolManager.parseMac(" 123456789abc "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseMac_invalidLength_tooShort() {
        WolManager.parseMac("12:34:56:78:9A")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseMac_invalidLength_tooLong() {
        WolManager.parseMac("12:34:56:78:9A:BC:DE")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseMac_invalidHexCharacters() {
        WolManager.parseMac("12:34:56:78:9A:ZZ")
    }

    @Test
    fun testBuildMagicPacket_payloadStructureAndShape() {
        val macBytes = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()
        )
        val packet = WolManager.buildMagicPacket(macBytes)

        // 1. Total length must be 102 bytes (6 + 16 * 6)
        assertEquals(102, packet.size)

        // 2. First 6 bytes must be 0xFF
        for (i in 0 until 6) {
            assertEquals("Byte $i should be 0xFF", 0xFF.toByte(), packet[i])
        }

        // 3. Exactly 16 repetitions of MAC
        for (rep in 0 until 16) {
            val offset = 6 + rep * 6
            for (byteIndex in 0 until 6) {
                assertEquals(
                    "Repetition $rep, byte $byteIndex should match MAC byte",
                    macBytes[byteIndex],
                    packet[offset + byteIndex]
                )
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValidatePort_zeroThrows() {
        WolManager.validatePort(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValidatePort_tooHighThrows() {
        WolManager.validatePort(65536)
    }

    @Test
    fun testValidatePort_valid() {
        assertEquals(9, WolManager.validatePort(9))
        assertEquals(65535, WolManager.validatePort(65535))
    }

    @Test
    fun testSendMagicPacketInternal_zeroRoutesReturnsFailure() {
        val dummyAddress = InetAddress.getByName("127.0.0.1")
        val result = WolManager.sendMagicPacketInternal(
            macStr = "AA:BB:CC:DD:EE:FF",
            port = 9,
            broadcastAddressesProvider = { listOf(dummyAddress) },
            packetSender = { _, _, _ -> 0 } // Simulates zero packets/routes sent
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Expected zero routes failure message in Ukrainian, got: $message",
            message.contains("жоден широкомовний маршрут недоступний")
        )
    }

    @Test
    fun testSendMagicPacketInternal_emptyAddressesReturnsFailure() {
        val result = WolManager.sendMagicPacketInternal(
            macStr = "AA:BB:CC:DD:EE:FF",
            port = 9,
            broadcastAddressesProvider = { emptyList() },
            packetSender = { _, _, addresses -> addresses.size }
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("жоден широкомовний маршрут недоступний"))
    }

    @Test
    fun testSendMagicPacketInternal_successfulSendsReturnCount() {
        val result = WolManager.sendMagicPacketInternal(
            macStr = "AA:BB:CC:DD:EE:FF",
            port = 9,
            broadcastAddressesProvider = {
                listOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName("192.168.1.255")
                )
            },
            packetSender = { _, _, _ -> 2 }
        )

        assertTrue(result.isSuccess)
        val text = result.getOrNull() ?: ""
        assertTrue("Expected 2 routes in message, got: $text", text.contains("2 маршрутів"))
    }

    @Test
    fun testSendMagicPacketInternal_invalidMacFailsBeforeSending() {
        var senderCalled = false
        val result = WolManager.sendMagicPacketInternal(
            macStr = "invalid-mac",
            port = 9,
            broadcastAddressesProvider = { listOf(InetAddress.getByName("255.255.255.255")) },
            packetSender = { _, _, _ ->
                senderCalled = true
                1
            }
        )

        assertTrue(result.isFailure)
        assertFalse("Sender should not be called when MAC is invalid", senderCalled)
    }
}
