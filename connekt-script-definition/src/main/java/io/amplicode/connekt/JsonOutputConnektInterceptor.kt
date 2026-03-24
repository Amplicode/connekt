package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.*
import okhttp3.Request
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class JsonOutputConnektInterceptor(
    printer: Printer,
    responseStorageDir: Path?,
    requestStorageDir: Path? = null
) : ConnektInterceptorBase(printer, responseStorageDir, requestStorageDir) {

    private val objectMapper = jacksonObjectMapper()
    private var requestCount = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val number = ++requestCount

        emitRequestEvent(request, number)

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            printer.println(objectMapper.writeValueAsString(
                mapOf("event" to "error", "number" to number, "message" to "Connection refused")
            ))
            throw e
        }

        emitResponseEvent(response, number)
        return response
    }

    private fun emitRequestEvent(request: Request, number: Int) {
        val headers = request.headers.map { listOf(it.first, it.second) }

        val bodyInfo = prepareRequestBody(request)

        val event = mutableMapOf(
            "event" to "request",
            "number" to number,
            "method" to request.method,
            "url" to request.url.toString(),
            "headers" to headers,
            "body" to bodyInfo.content,
            "bodyFile" to bodyInfo.filePath
        )

        printer.println(objectMapper.writeValueAsString(event))
    }

    private fun emitResponseEvent(response: Response, number: Int) {
        val headers = response.headers.map { listOf(it.first, it.second) }

        val bodyAndSavedTo = handleResponseBody(response)

        val event = mutableMapOf(
            "event" to "response",
            "number" to number,
            "status" to response.code,
            "statusText" to response.message,
            "protocol" to response.formatProtocol(),
            "headers" to headers,
            "body" to bodyAndSavedTo.content,
            "savedTo" to bodyAndSavedTo.filePath
        )

        printer.println(objectMapper.writeValueAsString(event))
    }

    /**
     * Mirrors the inline/file logic of [RawOutputConnektInterceptor.handleResponseBody].
     */
    private fun handleResponseBody(response: Response): ContentOrFile {
        val data = readResponseBuffer(response) ?: return ContentOrFile(null, null)

        val inlineBody: String? = if (data.shouldShowInline) {
            val charset: Charset = data.contentType?.charset() ?: StandardCharsets.UTF_8
            data.buffer.clone().readString(charset)
        } else {
            null
        }

        val savedTo = storeResponseToFile(
            response.request.url,
            data.contentDisposition,
            data.contentType,
            data.buffer.clone()
        )

        return ContentOrFile(inlineBody, savedTo)
    }
}
