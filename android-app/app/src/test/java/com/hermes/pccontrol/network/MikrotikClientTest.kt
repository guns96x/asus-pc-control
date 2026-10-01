package com.hermes.pccontrol.network

import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class MikrotikClientTest {

    private val validConfig = AppConfig(
        pcHost = "192.168.1.50",
        pcMac = "11:22:33:44:55:66",
        mikrotikHost = "192.168.88.1",
        mikrotikPort = 443,
        mikrotikUseHttps = true,
        mikrotikUser = "wol-admin",
        mikrotikPass = "SecretDummyPass123",
        mikrotikInterface = "bridge"
    )

    @Test
    fun testNormalizeMac_formatsCorrectly() {
        assertEquals("AA:BB:CC:DD:EE:FF", MikrotikClient.normalizeMac("aabbccddeeff"))
        assertEquals("AA:BB:CC:DD:EE:FF", MikrotikClient.normalizeMac("aa-bb-cc-dd-ee-ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", MikrotikClient.normalizeMac("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testValidateConfig_validConfigPasses() {
        val result = MikrotikClient.validateConfig(validConfig)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testValidateConfig_invalidHost() {
        val blankHost = validConfig.copy(mikrotikHost = "  ")
        assertTrue(MikrotikClient.validateConfig(blankHost).isFailure)

        val hostWithSlash = validConfig.copy(mikrotikHost = "192.168.88.1/rest")
        assertTrue(MikrotikClient.validateConfig(hostWithSlash).isFailure)

        val hostWithSpace = validConfig.copy(mikrotikHost = "192.168.88.1 bad")
        assertTrue(MikrotikClient.validateConfig(hostWithSpace).isFailure)
    }

    @Test
    fun testValidateConfig_invalidPort() {
        val zeroPort = validConfig.copy(mikrotikPort = 0)
        assertTrue(MikrotikClient.validateConfig(zeroPort).isFailure)

        val negativePort = validConfig.copy(mikrotikPort = -1)
        assertTrue(MikrotikClient.validateConfig(negativePort).isFailure)

        val overMaxPort = validConfig.copy(mikrotikPort = 65536)
        assertTrue(MikrotikClient.validateConfig(overMaxPort).isFailure)
    }

    @Test
    fun testValidateConfig_invalidInterface() {
        val blankInterface = validConfig.copy(mikrotikInterface = "")
        assertTrue(MikrotikClient.validateConfig(blankInterface).isFailure)

        val badInterface = validConfig.copy(mikrotikInterface = "bridge\nnewline")
        assertTrue(MikrotikClient.validateConfig(badInterface).isFailure)
    }

    @Test
    fun testValidateConfig_invalidUser() {
        val blankUser = validConfig.copy(mikrotikUser = "   ")
        assertTrue(MikrotikClient.validateConfig(blankUser).isFailure)
    }

    @Test
    fun testValidateConfig_invalidMac() {
        val badMac = validConfig.copy(pcMac = "not-a-mac")
        assertTrue(MikrotikClient.validateConfig(badMac).isFailure)
    }

    @Test
    fun testBuildUrl_schemesAndEndpoints() {
        val httpsUrl = MikrotikClient.buildUrl(validConfig.copy(mikrotikUseHttps = true, mikrotikPort = 443))
        assertEquals("https://192.168.88.1/rest/tool/wol", httpsUrl)

        val httpUrl = MikrotikClient.buildUrl(validConfig.copy(mikrotikUseHttps = false, mikrotikPort = 80))
        assertEquals("http://192.168.88.1/rest/tool/wol", httpUrl)
        assertEquals("http://192.168.88.1:8080/rest/tool/wol",
            MikrotikClient.buildUrl(validConfig.copy(mikrotikUseHttps = false, mikrotikPort = 8080)))
    }

    @Test
    fun testBuildAuthHeader_encodesCredentials() {
        val authHeader = MikrotikClient.buildAuthHeader("admin", "password123")
        // "admin:password123" base64 is "YWRtaW46cGFzc3dvcmQxMjM="
        assertEquals("Basic YWRtaW46cGFzc3dvcmQxMjM=", authHeader)
    }

    @Test
    fun testBuildWolJson_generatesExpectedFormat() {
        val json = MikrotikClient.buildWolJson("11-22-33-44-55-66", "ether1")
        assertEquals("""{"mac":"11:22:33:44:55:66","interface":"ether1"}""", json)
    }

    @Test
    fun testMapHttpError_usefulUkrainianMessagesWithoutRawBodyEcho() {
        val err401 = MikrotikClient.mapHttpError(401)
        assertTrue(err401.contains("Помилка автентифікації MikroTik"))
        assertTrue(err401.contains("401"))

        val err403 = MikrotikClient.mapHttpError(403)
        assertTrue(err403.contains("Помилка автентифікації MikroTik"))

        val err404 = MikrotikClient.mapHttpError(404)
        assertTrue(err404.contains("RouterOS") || err404.contains("не підтримує") || err404.contains("недоступний"))

        val err400 = MikrotikClient.mapHttpError(400)
        assertTrue(err400.contains("Невірні параметри"))

        val err500 = MikrotikClient.mapHttpError(500)
        assertTrue(err500.contains("Внутрішня помилка сервера MikroTik"))
    }

    @Test
    fun testMapNetworkException_usefulUkrainianMessages() {
        val unknownHost = MikrotikClient.mapNetworkException(UnknownHostException("test.local"))
        assertTrue(unknownHost.contains("хост MikroTik"))

        val timeout = MikrotikClient.mapNetworkException(SocketTimeoutException("Read timed out"))
        assertTrue(timeout.contains("Перевищено час очікування"))

        val refused = MikrotikClient.mapNetworkException(ConnectException("Connection refused"))
        assertTrue(refused.contains("Не вдалося з'єднатися з MikroTik"))

        val ssl = MikrotikClient.mapNetworkException(SSLException("Handshake failed"))
        assertTrue(ssl.contains("безпечного з'єднання (TLS/SSL)"))
    }

    @Test
    fun testSendWol_success200() = runBlocking {
        var responseClosed = false
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(object : ResponseBody() {
                        private val source = object : ForwardingSource(Buffer().writeUtf8("{}")) {
                            override fun close() {
                                responseClosed = true
                                super.close()
                            }
                        }.buffer()
                        override fun contentType() = "application/json".toMediaType()
                        override fun contentLength() = 2L
                        override fun source() = source
                    })
                    .build()
            })
            .build()

        val result = MikrotikClient.sendWol(validConfig, testClient)
        assertTrue(result.isSuccess)
        assertEquals("MikroTik WOL відправлено успішно!", result.getOrNull())
        assertTrue("Response body must be closed", responseClosed)
    }

    @Test
    fun testSendWol_authFailure401_doesNotEchoCredentialsOrRawServerBody() = runBlocking {
        val secretRawServerBody = "<title>RouterOS Internal Error 401</title><secret>RouterOSLeak</secret>"
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(secretRawServerBody.toResponseBody("text/html".toMediaType()))
                    .build()
            })
            .build()

        val result = MikrotikClient.sendWol(validConfig, testClient)
        assertTrue(result.isFailure)

        val errorMsg = result.exceptionOrNull()?.message ?: ""
        // Check message is useful in Ukrainian
        assertTrue(errorMsg.contains("Помилка автентифікації MikroTik"))
        // Check raw server body is NOT echoed
        assertFalse("Error message should not echo raw server body", errorMsg.contains("RouterOSLeak"))
        assertFalse("Error message should not echo raw server body", errorMsg.contains(secretRawServerBody))
        // Check password is NOT echoed
        assertFalse("Error message should not echo password", errorMsg.contains(validConfig.mikrotikPass))
    }

    @Test
    fun testSendWol_notFound404_unsupportedRouterOsError() = runBlocking {
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("Endpoint not found".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()

        val result = MikrotikClient.sendWol(validConfig, testClient)
        assertTrue(result.isFailure)

        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue(errorMsg.contains("HTTP 404"))
        assertFalse("Raw body should not be echoed", errorMsg.contains("Endpoint not found"))
    }

    @Test
    fun testSendWol_networkTimeoutException() = runBlocking {
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { _ ->
                throw SocketTimeoutException("connect timed out")
            })
            .build()

        val result = MikrotikClient.sendWol(validConfig, testClient)
        assertTrue(result.isFailure)

        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue(errorMsg.contains("Перевищено час очікування"))
        assertFalse(errorMsg.contains(validConfig.mikrotikPass))
    }

    @Test
    fun testSendWol_invalidConfigShortCircuitsNetwork() = runBlocking {
        var networkAttempted = false
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                networkAttempted = true
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val invalidConfig = validConfig.copy(pcMac = "invalid")
        val result = MikrotikClient.sendWol(invalidConfig, testClient)

        assertTrue(result.isFailure)
        assertFalse("Network should never be attempted when validation fails", networkAttempted)
    }
}
