package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplicode.connekt.Printer.Color.*
import io.amplicode.connekt.utils.ContentDisposition
import io.amplicode.connekt.utils.uniqueFilePath
import io.amplicode.connekt.utils.filename
import io.amplicode.connekt.utils.okhttp.contentDisposition
import io.amplicode.connekt.utils.okhttp.fileExtension
import io.amplicode.connekt.utils.okhttp.hasSubTypeOf
import okhttp3.*
import okhttp3.Protocol
import okhttp3.Request
import okio.Buffer
import okio.GzipSource
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

class CallHandler(
    private val printer: Printer,
    private val responseStorageDir: Path?
) : Interceptor {
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
        val body = request.body ?: return
        val sink = Buffer()
        body.writeTo(sink)

        ByteArrayOutputStream().use { stream ->
            sink.copyTo(stream)
            printer.print(String(stream.toByteArray()), GREEN)
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
        val protocol = when (response.protocol) {
            Protocol.HTTP_1_0 -> "HTTP/1.0"
            Protocol.HTTP_1_1 -> "HTTP/1.1"
            Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
            else -> response.protocol.name
        }
        val text = listOfNotNull(
            protocol,
            response.code,
            response.message
        ).joinToString(separator = " ")
        printer.println(text, BLUE)
    }

    private fun handleResponseBody(response: Response) {
        printer.println("")
        val responseBody = response.body ?: return
        // Don't handle body from redirection response
        if (response.isRedirect) {
            return
        }
        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        var buffer = source.buffer

        if ("gzip".equals(response.headers["Content-Encoding"], ignoreCase = true)) {
            GzipSource(buffer.clone()).use { gzippedResponseBody ->
                buffer = Buffer()
                buffer.writeAll(gzippedResponseBody)
            }
        }

        val contentLength = responseBody.contentLength()
        if (contentLength == 0L) {
            return
        }
        val contentType = responseBody.contentType()
        val contentDisposition = response.contentDisposition()

        if (contentDisposition?.filename() == null && contentLength in (0..300)) {
            logResponse(contentType, buffer.clone())
        }

        val responseStorageDir = responseStorageDir
        if (responseStorageDir != null) {
            val responseFilename = calcResponseFilename(
                response.request.url,
                contentDisposition,
                contentType
            )
            storeResponse(
                responseStorageDir,
                responseFilename,
                buffer.clone()
            )
        }
    }

    private fun logResponse(contentType: MediaType?, responseBuffer: Buffer) {
        val charset: Charset = contentType?.charset() ?: StandardCharsets.UTF_8
        val responseText = responseBuffer.readString(charset)

        val logText = when {
            contentType?.hasSubTypeOf("json") == true -> try {
                // Apply pretty format if JSON
                val objectMapper = jacksonObjectMapper()
                val node = objectMapper.readTree(responseText)
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node)
            } catch (_: Exception) {
                responseText
            }

            else -> responseText
        }
        printer.println(logText, GREEN)
    }

    private fun storeResponse(
        responseStorageDir: Path,
        responseFilename: String,
        responseBuffer: Buffer
    ) {
        if (!responseStorageDir.exists()) {
            responseStorageDir.createDirectories()
        }

        val filePath = uniqueFilePath(responseStorageDir, responseFilename)
        filePath.outputStream().use { outputStream ->
            responseBuffer.copyTo(outputStream)
        }

        printer.println("")
        printer.println("Response file saved.", GREEN)
        printer.println("> ${filePath.toFile()}", GREEN)
    }

    private fun calcResponseFilename(
        requestUrl: HttpUrl,
        contentDisposition: ContentDisposition?,
        contentType: MediaType?
    ): String {
        contentDisposition?.filename()
            ?.let { return it }

        requestUrl.filename()
            ?.let { return it }

        val fileExtension = contentType?.fileExtension() ?: "txt"
        return getCurrentTimestamp() + "." + fileExtension
    }

    /**
     * @return the filename from the last path segment of the request URL, or null if not found.
     */
    private fun HttpUrl.filename(): String? = this
        .pathSegments
        .lastOrNull()
        ?.takeIf { segment -> '.' in segment }
        ?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        }

    private fun getCurrentTimestamp(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
        return current.format(formatter)
    }

    private fun logHeaders(headers: Headers) {
        headers.joinToString(separator = " \n") { it.first + ": " + it.second }
            .let { printer.println(it, BLUE) }
    }
}