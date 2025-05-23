package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektLifeCycleCallbacksImpl
import io.amplicode.connekt.context.CookiesContextImpl
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.SetCookieRequest
import io.amplicode.connekt.test.utils.server.TestServer
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CookiesTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `check cookies are stored into file`() {
        val storageFile = createTempFile("connekt-cookie-test-")

        val cookiesToSet = listOf(
            Cookie("fooCookie", "foo", path = "/"),
            Cookie("barCookie", "bar", path = "/"),
            Cookie("bazCookie", "baz", path = "/")
        )

        runScript(
            context = testConnektContext(
                cookiesContextFactory = {
                    CookiesContextImpl(storageFile, it)
                }
            )
        ) {
            // Make server to set cookies for our client
            POST("$host/cookies/set") {
                header("Content-Type", "application/json")

                val body = SetCookieRequest(cookiesToSet)
                body(Json.Default.encodeToString(body))
            }.then {
                assertOk()
            }

            // Request with cookies
            GET("$host/foo") {
                configureClient {
                    interceptRequest {
                        it.assertHeader(
                            "Cookie",
                            "fooCookie=foo; barCookie=bar; bazCookie=baz"
                        )
                    }
                }
            }.then {
                assertOk()
            }

            // Request with no cookies
            GET("$host/foo") {
                noCookies()
                configureClient {
                    interceptRequest {
                        it.assertHeader("Cookie", null)
                    }
                }
            }
        }

        // Check that cookies are persisted
        assertStorageFileContainsCookies(storageFile, cookiesToSet)
    }

    private fun assertStorageFileContainsCookies(storageFile: Path, cookiesToSet: List<Cookie>) {
        val lifeCycleCallbacks = ConnektLifeCycleCallbacksImpl()

        val cookiesContextThen = CookiesContextImpl(
            storageFile,
            lifeCycleCallbacks
        )

        val cookies = cookiesContextThen.cookieJar
            .loadForRequest("$host/foo".toHttpUrl())

        cookiesToSet.forEach { expectedCookie ->
            val actualCookie = cookies.find {
                it.name == expectedCookie.name
            }

            assertNotNull(
                actualCookie,
                "Expected cookie '${expectedCookie.name}' to exist"
            )
            assertEquals(
                expectedCookie.value,
                actualCookie.value,
                "Different values for cookie: ${expectedCookie.name}"
            )
        }

        lifeCycleCallbacks.fireClosed()
    }
}

private fun OkHttpClient.Builder.interceptRequest(operation: (okhttp3.Request) -> Unit) {
    addNetworkInterceptor { chain ->
        val request = chain.request()
        operation(request)
        chain.proceed(request)
    }
}

fun okhttp3.Request.assertHeader(name: String, value: String?) {
    assertEquals(value, headers[name])
}

fun Response.assertOk() = assertEquals(200, code)