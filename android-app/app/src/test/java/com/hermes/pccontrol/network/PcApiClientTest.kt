package com.hermes.pccontrol.network

import com.hermes.pccontrol.data.AppConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class PcApiClientTest {
    private val config = AppConfig(pcHost = "primary.test", pcFallbackHost = "fallback.test", authToken = "test")
    private fun response(request: Request, code: Int = 200, body: String = "") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody()).build()
    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .retryOnConnectionFailure(false).addInterceptor { handler(it.request()) }.build()

    @Test fun readTimeoutNeverReplaysHardwareActions() = runBlocking {
        for (path in listOf("/api/power/hibernate", "/api/lighting/dark", "/api/monitor/sleep", "/api/keyboard/toggle")) {
            val attempts = mutableListOf<String>()
            val api = PcApiClient(client { attempts.add(it.url.host); throw SocketTimeoutException("timeout") })
            val result = api.callWithFallback(config, path, "POST") { _, _, _ -> true }
            assertTrue(result.isFailure)
            assertEquals(listOf("primary.test"), attempts)
        }
    }

    @Test fun connectTimeoutCanUseFallbackBeforeActionWasSent() = runBlocking {
        val attempts = mutableListOf<String>()
        val api = PcApiClient(client {
            attempts.add(it.url.host)
            if (it.url.host == "primary.test") throw SocketTimeoutException("connect timed out")
            response(it)
        })
        assertTrue(api.hibernate(config).isSuccess)
        assertEquals(listOf("primary.test", "fallback.test"), attempts)
    }

    @Test fun postServerFailureIsNotReplayed() = runBlocking {
        var attempts = 0
        val api = PcApiClient(client { attempts++; response(it, 503, """{"error":"driver unavailable"}""") })
        assertTrue(api.hibernate(config).isFailure)
        assertEquals(1, attempts)
    }

    @Test fun statusCanUseFallbackAfterUnavailablePrimary() = runBlocking {
        val attempts = mutableListOf<String>()
        val api = PcApiClient(client {
            attempts.add(it.url.host)
            if (it.url.host == "primary.test") response(it, 503)
            else response(it, body = """{"hostname":"ASUS","timestamp":100,"keyboard_level":3}""")
        })
        val status = api.getStatus(config).getOrThrow()
        assertEquals("fallback.test", status.connectedHost)
        assertEquals(3, status.keyboardLevel)
        assertEquals(2, attempts.size)
    }

    @Test fun savedHostChangeDoesNotReuseOldCachedDestination() {
        val api = PcApiClient()
        api.activeHost = "old.test"
        assertEquals(listOf("primary.test", "fallback.test"), api.getCandidates(config))
    }

    @Test fun authenticationFailureDoesNotTryOtherHosts() = runBlocking {
        var attempts = 0
        val api = PcApiClient(client { attempts++; response(it, 401) })
        assertTrue(api.getStatus(config).isFailure)
        assertEquals(1, attempts)
    }

    @Test fun invalidKeyboardLevelNeverSendsACommand() = runBlocking {
        var attempts = 0
        val api = PcApiClient(client { attempts++; response(it) })
        assertTrue(api.setKeyboardLevel(config, 9).isFailure)
        assertEquals(0, attempts)
    }

    @Test fun keyboardResponseMustContainHardwareReadback() = runBlocking {
        var attempts = 0
        val api = PcApiClient(client { attempts++; response(it, body = """{"success":true}""") })
        assertTrue(api.setKeyboardLevel(config, 3).isFailure)
        assertEquals(1, attempts)
    }

    @Test fun incompleteStatusDoesNotShowAnOnlineComputer() = runBlocking {
        val api = PcApiClient(client { response(it, body = "{}") })
        assertTrue(api.getStatus(config).isFailure)
    }

    @Test fun otaRejectsMissingReleaseMetadata() = runBlocking {
        assertTrue(OtaManager.checkUpdate(config, client { response(it, body = "{}") }).isFailure)
    }

    @Test fun otaParsesPublishedRelease() = runBlocking {
        val release = OtaManager.checkUpdate(config, client {
            response(it, body = """{"version_code":6,"version_name":"1.1.4","download_url":"/app.apk"}""")
        }).getOrThrow()
        assertEquals(6, release.versionCode)
        assertEquals("/app.apk", release.downloadUrl)
    }

    @Test fun invalidPerformanceModeNeverSendsACommand() = runBlocking {
        var attempts = 0
        val api = PcApiClient(client { attempts++; response(it) })
        assertTrue(api.setPerformanceMode(config, 5).isFailure)
        assertEquals(0, attempts)
    }

    @Test fun performanceModeParsesHardwareReadback() = runBlocking {
        val api = PcApiClient(client { response(it, body = """{"success":true,"mode":1,"mode_name":"turbo"}""") })
        val res = api.setPerformanceMode(config, 1).getOrThrow()
        assertEquals(1, res)
    }
}
