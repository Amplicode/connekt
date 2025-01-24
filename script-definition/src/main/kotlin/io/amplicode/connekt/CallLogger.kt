package io.amplicode.connekt

import okhttp3.*
import okio.Buffer
import okio.GzipSource
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

class CallLogger(private val stream: PrintStream) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        logRequest(request)

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            stream.println(color("Connection refused", Color.RED))
            throw e
        }

        logResponse(response)

        return response
    }

    private fun logRequest(request: Request) {
        listOfNotNull(request.method, request.url)
            .joinToString(separator = " ")
            .let { stream.println(color(it, Color.BLUE)) }

        logHeaders(request.headers)

        stream.println("")

        request.body?.let {
            val sink = Buffer()
            it.writeTo(sink)

            stream.print(Color.GREEN.ansi())
            sink.copyTo(stream)
            stream.print(RESET_COLOR)
            stream.println("")
            stream.println("")
        }
    }

    private fun logResponse(response: Response) {
        stream.println(
            listOfNotNull(printProtocol(response.protocol), response.code, response.message)
                .joinToString(separator = " ")
                .let { color(it, Color.BLUE) }
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
            logResponseBody(responseBody, buffer)
        }
    }

    private fun logResponseBody(responseBody: ResponseBody, buffer: Buffer) {
        val contentType = responseBody.contentType()
        val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8


        val fileExtension = contentType?.toString()?.let { getExtensionForFileTypes(it) }


        stream.println("")
        stream.print(Color.GREEN.ansi())

        if (fileExtension != null) {
            val downloadDir = File(System.getProperty("user.home"), ".connekt")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val fileName = File(downloadDir, getCurrentTimestamp() + "." + fileExtension)

            FileOutputStream(fileName).use {
                buffer.clone().copyTo(it)
            }

            stream.println("Response file saved.\n> $fileName")
        } else {
            stream.println(buffer.clone().readString(charset))
        }


        stream.print(RESET_COLOR)
    }

    fun getCurrentTimestamp(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
        return current.format(formatter)
    }

    private fun logHeaders(headers: Headers) {
        headers.joinToString(separator = " \n") { it.first + ": " + it.second }
            .let { stream.println(color(it, Color.BLUE)) }
    }

    private fun printProtocol(protocol: Protocol) = when (protocol) {
        Protocol.HTTP_1_0 -> "HTTP/1.0"
        Protocol.HTTP_1_1 -> "HTTP/1.1"
        Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
        else -> protocol.name
    }

    companion object {
        private fun color(text: String, color: Color): String {
            return "${color.ansi()}$text$RESET_COLOR"
        }

        private const val RESET_COLOR = "\u001B[0m"

        private enum class Color(val code: String) {
            BLUE("34"),
            GREEN("32"),
            RED("31");

            fun ansi(): String {
                return "\u001B[${code}m";
            }
        }

        private val fileMediaTypes = listOf(
            "image",
            "video",
            "audio",
            "model",
        )

        private fun getExtensionForFileTypes(contentType: String): String? {
            if (fileMediaTypes.any { contentType.startsWith("$it/") }) {
                return contentType.substringAfter("/").substringBefore(" ")
            }

            if (contentType == "application/pdf")
                return "pdf";

            return null;
        }
    }
}
