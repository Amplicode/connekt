package io.amplicode.connekt.context

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.Closeable
import java.io.Serializable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface CookiesContext {
    val cookieJar: CookieJar
}

object NoopCookiesContext : CookiesContext {
    override val cookieJar: CookieJar = CookieJar.NO_COOKIES
}

class CookiesContextImpl(storage: Path) : CookiesContext, AutoCloseable {

    private val cookieJarImpl = PersistentCookieJar(storage)

    override val cookieJar: CookieJar = cookieJarImpl

    override fun close() {
        cookieJarImpl.close()
    }
}

private class PersistentCookieJar(private val filePath: Path) : CookieJar, Closeable {

    private val objectMapper = ObjectMapper()
    private val cookiesMap = ConcurrentHashMap<String, Set<Cookie>>()

    init {
        val storage = runCatching {
            objectMapper.readValue(
                filePath.toFile(),
                CookiesStorage::class.java
            )
        }.getOrElse { CookiesStorage(emptyList()) }

        storage.cookies
            .map { it.toCookie() }
            .groupBy { it.domain }
            .forEach { (domain, cookies) ->
                cookiesMap[domain] = cookies.toSet()
            }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = cookiesMap.getOrElse(url.host) {
            return emptyList()
        }
        return hostCookies.filter {
            it.matches(url)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookiesMap.compute(url.host) { _, hostCookies ->
            (hostCookies ?: setOf()) + cookies
        }
    }

    override fun close() {
        val value = CookiesStorage(
            cookiesMap.values
                .flatten()
                .map(::SerializableCookie))
        objectMapper.writeValue(filePath.toFile(), value)
    }
}

data class CookiesStorage(val cookies: List<SerializableCookie> = emptyList()) : Serializable

data class SerializableCookie(
    val name: String = "",
    val value: String = "",
    val domain: String = "",
    val path: String = "",
    val expiresAt: Long = 0L,
    val secure: Boolean = false,
    val httpOnly: Boolean = false
) : Serializable {

    constructor(cookie: Cookie) : this(
        name = cookie.name,
        value = cookie.value,
        domain = cookie.domain,
        path = cookie.path,
        expiresAt = cookie.expiresAt,
        secure = cookie.secure,
        httpOnly = cookie.httpOnly
    )

    fun toCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain(domain)
        .path(path)
        .expiresAt(expiresAt)
        .apply {
            if (secure) secure()
            if (httpOnly) httpOnly()
        }
        .build()
}