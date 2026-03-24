package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplicode.connekt.Printer.Color.*
import io.amplicode.connekt.utils.okhttp.hasSubTypeOf
import okhttp3.*
import okhttp3.Request
import okio.Buffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class RawOutputConnektInterceptor(
    printer: Printer,
    responseStorageDir: Path?,
    requestStorageDir: Path? = null
) : ConnektInterceptorBase(printer, responseStorageDir, requestStorageDir) {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        logRequest(request)

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            printer.println("Connection refused", RED)
            throw e
        }
        logResponse(response)

        return response
    }

    private fun logRequest(request: Request) {
        logRequestUrl(request)
        logHeaders(request.headers)
        printer.println()
        logRequestBody(request)
    }

    private fun logRequestUrl(request: Request) {
        sequenceOf(request.method, request.url)
            .joinToString(separator = " ")
            .let { printer.println(it, BLUE) }
    }

    private fun logRequestBody(request: Request) {
        val body = handleRequestBody(request)
        if (body.content == null && body.filePath == null) return
        if (body.filePath != null) {
            printer.println("Request body saved.", GREEN)
            printer.println("> ${body.filePath}", GREEN)
        } else {
            printer.print(body.content!!, GREEN)
        }
        printer.println()
        printer.println()
    }

    private fun logResponse(response: Response) {
        logResponseStatus(response)
        logHeaders(response.headers)
        handleResponseBody(response)
    }

    private fun logResponseStatus(response: Response) {
        val text = listOfNotNull(
            response.formatProtocol(),
            response.code,
            response.message
        ).joinToString(separator = " ")
        printer.println(text, BLUE)
    }

    private fun handleResponseBody(response: Response) {
        printer.println("")
        val data = readResponseBuffer(response) ?: return

        if (data.shouldShowInline) {
            logResponseBody(data.contentType, data.buffer.clone())
        }

        val savedPath = storeResponseToFile(
            response.request.url,
            data.contentDisposition,
            data.contentType,
            data.buffer.clone()
        )
        if (savedPath != null) {
            printer.println("")
            printer.println("Response file saved.", GREEN)
            printer.println("> $savedPath", GREEN)
        }
    }

    private fun logResponseBody(contentType: MediaType?, responseBuffer: Buffer) {
        val charset: Charset = contentType?.charset() ?: StandardCharsets.UTF_8
        val responseText = responseBuffer.readString(charset)

        val logText = when {
            contentType?.hasSubTypeOf("json") == true -> try {
                val objectMapper = jacksonObjectMapper()
                val node = objectMapper.readTree(responseText)
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
            } catch (_: Exception) {
                responseText
            }

            else -> responseText
        }
        printer.println(logText, GREEN)
    }

    private fun logHeaders(headers: Headers) {
        headers.joinToString(separator = " \n") { it.first + ": " + it.second }
            .let { printer.println(it, BLUE) }
    }
}
