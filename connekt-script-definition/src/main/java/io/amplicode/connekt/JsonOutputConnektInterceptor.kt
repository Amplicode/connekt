package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.*
import okhttp3.Request
import java.nio.file.Path
import java.util.UUID

class JsonOutputConnektInterceptor(
    printer: Printer,
    responseStorageDir: Path?,
    requestStorageDir: Path? = null
) : ConnektInterceptorBase(printer, responseStorageDir, requestStorageDir) {

    private val objectMapper = jacksonObjectMapper()
    private var requestCount = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = UUID.randomUUID().toString()
        val idx = ++requestCount

        emitRequestEvent(request, id, idx)

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            printer.println(objectMapper.writeValueAsString(
                mapOf(
                    "event" to "error",
                    "id" to id,
                    "index" to idx,
                    "message" to "Connection refused")
            ))
            throw e
        }

        emitResponseEvent(response, id, idx)
        return response
    }

    private fun emitRequestEvent(request: Request, id: String, idx: Int) {
        val headers = request.headers.map { listOf(it.first, it.second) }

        val bodyInfo = prepareRequestBody(request)

        val event = mutableMapOf(
            "event" to "request",
            "id" to id,
            "index" to idx,
            "method" to request.method,
            "url" to request.url.toString(),
            "headers" to headers,
            "body" to bodyInfo.content,
            "savedTo" to bodyInfo.filePath
        )

        printer.println(objectMapper.writeValueAsString(event))
    }

    private fun emitResponseEvent(response: Response, id: String, idx: Int) {
        val headers = response.headers.map { listOf(it.first, it.second) }

        val bodyInfo = prepareResponseBody(response)

        val event = mutableMapOf(
            "event" to "response",
            "id" to id,
            "index" to idx,
            "status" to response.code,
            "statusText" to response.message,
            "protocol" to response.formatProtocol(),
            "headers" to headers,
            "body" to bodyInfo.content,
            "savedTo" to bodyInfo.filePath
        )

        printer.println(objectMapper.writeValueAsString(event))
    }
}
