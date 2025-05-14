package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplicode.connekt.Printer.Color.*
import okhttp3.*
import okhttp3.Request
import okio.Buffer
import okio.GzipSource
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

class CallLogger(
    private val printer: Printer,
    private val responseStorageDir: Path?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        logRequest(request)

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            printer.println("Connection refused", RED)
            throw e
        }

        logResponse(response)

        return response
    }

    private fun logRequest(request: Request) {
        listOfNotNull(request.method, request.url)
            .joinToString(separator = " ")
            .let { printer.println(it, BLUE) }

        logHeaders(request.headers)

        printer.println("")

        request.body?.let {
            val sink = Buffer()
            it.writeTo(sink)

            ByteArrayOutputStream().use { stream ->
                sink.copyTo(stream)

                printer.print(String(stream.toByteArray()), GREEN)
            }
            printer.println("")
            printer.println("")
        }
    }

    private fun logResponse(response: Response) {
        printer.println(
            listOfNotNull(
                printProtocol(response.protocol),
                response.code,
                response.message
            ).joinToString(separator = " "),
            BLUE
        )

        logHeaders(response.headers)

        val responseBody = response.body!!
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

        if (contentLength != 0L) {
            logResponseBody(response.headers, responseBody, buffer)
        }
    }

    private fun logResponseBody(headers: Headers, responseBody: ResponseBody, buffer: Buffer) {
        val contentType = responseBody.contentType()
        val fileExtension = contentType?.let(::getExtensionForFileTypes) ?: "txt"
        val contentDisposition = headers["Content-Disposition"]
            ?.let { ContentDisposition.parseFromHeader(it) }

        val contentDispositionFilename = contentDisposition?.filename()

        if (contentDispositionFilename == null || responseBody.contentLength() <= 300) {
            printer.println("")
            val charset: Charset = contentType?.charset() ?: StandardCharsets.UTF_8
            var responseText = buffer.clone().readString(charset)
            // Apply pretty format if JSON
            if (contentType?.hasSubTypeOf("json") == true) {
                try {
                    val objectMapper = jacksonObjectMapper()
                    val node = objectMapper.readTree(responseText)
                    responseText = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(node)
                } catch (_: Exception) {
                    // ignore
                }
            }
            printer.println(responseText, GREEN)
        }

        val responseStorageDir = responseStorageDir
        if (responseStorageDir != null) {
            if (!responseStorageDir.exists()) {
                responseStorageDir.createDirectories()
            }

            // Determine filename - use content disposition filename if available, otherwise use timestamp
            val fileName = if (!contentDispositionFilename.isNullOrBlank()) {
                contentDispositionFilename
            } else {
                getCurrentTimestamp() + "." + fileExtension
            }

            // Create a unique file path
            val filePath = createFileStorePath(responseStorageDir, fileName)
            filePath.outputStream().use { outputStream ->
                buffer.clone().copyTo(outputStream)
            }

            printer.println("")
            printer.println("Response file saved.", GREEN)
            printer.println("> file://${filePath.toFile()}", GREEN)
        }
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

    private fun printProtocol(protocol: Protocol) = when (protocol) {
        Protocol.HTTP_1_0 -> "HTTP/1.0"
        Protocol.HTTP_1_1 -> "HTTP/1.1"
        Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
        else -> protocol.name
    }

    private fun createFileStorePath(parentDir: Path, originalFileName: String): Path {
        var counter = 0
        var fileName = originalFileName
        var filePath = parentDir.resolve(fileName)

        while (filePath.toFile().exists()) {
            counter++
            val lastDotIndex = originalFileName.lastIndexOf('.')
            if (lastDotIndex != -1) {
                val name = originalFileName.substring(0, lastDotIndex)
                val extension = originalFileName.substring(lastDotIndex)
                fileName = "$name-$counter$extension"
            } else {
                fileName = "$originalFileName-$counter"
            }
            filePath = parentDir.resolve(fileName)
        }

        return filePath
    }
}

private val fileMediaTypes = listOf(
    "image",
    "video",
    "audio",
    "model",
)

private fun getExtensionForFileTypes(contentType: MediaType): String? = when {
    fileMediaTypes.any(contentType::hasTypeOf) -> contentType.subtype
    contentType.hasSubTypeOf("pdf") -> "pdf"
    else -> null
}

fun MediaType.hasSubTypeOf(subtype: String) =
    this.subtype.equals(subtype, ignoreCase = true)

fun MediaType.hasTypeOf(type: String) =
    this.type.equals(type, ignoreCase = true)

data class ContentDisposition(
    val type: String,
    val parameters: Map<String, String>
) {
    companion object {
        fun parseFromHeader(header: String): ContentDisposition {
            val parts = header.split(";")
            val type = parts[0].trim()

            val params = parts.drop(1).associate { param ->
                val (key, value) = param.trim().split("=", limit = 2)
                key to value.trim('"')
            }

            return ContentDisposition(type, params)
        }
    }
}

fun ContentDisposition.name() = parameters["name"]
fun ContentDisposition.filename() = parameters["filename"]