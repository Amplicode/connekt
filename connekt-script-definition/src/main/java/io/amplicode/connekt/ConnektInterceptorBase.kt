package io.amplicode.connekt

import io.amplicode.connekt.utils.ContentDisposition
import io.amplicode.connekt.utils.filename
import io.amplicode.connekt.utils.okhttp.contentDisposition
import io.amplicode.connekt.utils.okhttp.fileExtension
import io.amplicode.connekt.utils.uniqueFilePath
import okhttp3.*
import okhttp3.Request
import okio.Buffer
import okio.GzipSource
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

/**
 * Common base for [RawOutputConnektInterceptor] and [JsonOutputConnektInterceptor].
 *
 * Provides shared utilities for reading the response body (with gzip support),
 * persisting responses to the file system, and deriving file names.
 */
abstract class ConnektInterceptorBase(
    protected val printer: Printer,
    protected val responseStorageDir: Path?,
    protected val requestStorageDir: Path? = null
) : Interceptor {

    /**
     * Reads the response body into an in-memory buffer, decompressing gzip if needed.
     *
     * Returns `null` when the body should be ignored: redirect responses, absent body,
     * or zero content-length.
     */
    protected fun readResponseBuffer(response: Response): ResponseBodyData? {
        val responseBody = response.body ?: return null
        if (response.isRedirect) return null

        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        var buffer = source.buffer

        if ("gzip".equals(response.headers["Content-Encoding"], ignoreCase = true)) {
            GzipSource(buffer.clone()).use { gzipped ->
                buffer = Buffer()
                buffer.writeAll(gzipped)
            }
        }

        val contentLength = responseBody.contentLength()
        if (contentLength == 0L) return null

        return ResponseBodyData(
            buffer = buffer,
            contentLength = contentLength,
            contentType = responseBody.contentType(),
            contentDisposition = response.contentDisposition()
        )
    }

    /**
     * Saves [responseBuffer] to a file inside [responseStorageDir] and returns its absolute path.
     *
     * Returns `null` when [responseStorageDir] is `null`.
     */
    protected fun storeResponseToFile(
        requestUrl: HttpUrl,
        contentDisposition: ContentDisposition?,
        contentType: MediaType?,
        responseBuffer: Buffer
    ): String? {
        val dir = responseStorageDir ?: return null
        if (!dir.exists()) dir.createDirectories()
        val filename = calcResponseFilename(requestUrl, contentDisposition, contentType)
        val filePath = uniqueFilePath(dir, filename)
        filePath.outputStream().use { responseBuffer.copyTo(it) }
        return filePath.toFile().absolutePath
    }

    protected fun prepareRequestBody(request: Request): ContentOrFile {
        val body = request.body ?: return ContentOrFile(null, null, null)
        val sink = Buffer()
        body.writeTo(sink)

        val contentType = body.contentType()
        val bytes = sink.readByteArray()
        if (bytes.size >= BODY_THRESHOLD) {
            val filePath = storeRequestToFile(bytes, contentType)
            if (filePath != null) return ContentOrFile(null, filePath, contentType)
        }

        val charset = contentType?.charset() ?: StandardCharsets.UTF_8
        return ContentOrFile(String(bytes, charset), null, contentType)
    }

    protected fun storeRequestToFile(bytes: ByteArray, contentType: MediaType? = null): String? {
        val dir = requestStorageDir ?: return null
        if (!dir.exists()) dir.createDirectories()
        val filename = calcRequestFilename(contentType)
        val filePath = uniqueFilePath(dir, filename)
        filePath.outputStream().use { it.write(bytes) }
        return filePath.toFile().absolutePath
    }

    protected fun calcRequestFilename(
        contentType: MediaType?
    ): String {
        val extension = contentType?.fileExtension() ?: "txt"
        return "${getCurrentTimestamp()}.request.$extension"
    }

    protected fun prepareResponseBody(response: Response): ContentOrFile {
        val data = readResponseBuffer(response) ?: return ContentOrFile(null, null, null)

        val inlineBody: String? = if (data.shouldShowInline) {
            val charset: Charset = data.contentType?.charset() ?: StandardCharsets.UTF_8
            val body = data.buffer.clone().readString(charset)
            if (body.length < BODY_THRESHOLD) body else null
        } else {
            null
        }

        val savedTo = storeResponseToFile(
            response.request.url,
            data.contentDisposition,
            data.contentType,
            data.buffer.clone()
        )

        return ContentOrFile(inlineBody, savedTo, data.contentType)
    }


    protected fun calcResponseFilename(
        requestUrl: HttpUrl,
        contentDisposition: ContentDisposition?,
        contentType: MediaType?
    ): String {
        contentDisposition?.filename()?.let { return it }
        requestUrl.urlFilename()?.let { return it }
        val fileExtension = contentType?.fileExtension() ?: "txt"
        return getCurrentTimestamp() + "." + fileExtension
    }

    protected fun HttpUrl.urlFilename(): String? = pathSegments
        .lastOrNull()
        ?.takeIf { '.' in it }
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

    protected fun getCurrentTimestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss"))

    protected fun Response.formatProtocol(): String = when (protocol) {
        Protocol.HTTP_1_0 -> "HTTP/1.0"
        Protocol.HTTP_1_1 -> "HTTP/1.1"
        Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
        else -> protocol.name
    }

    companion object {
        const val BODY_THRESHOLD = 2000
    }
}

data class ContentOrFile(
    val content: String?,
    val filePath: String?,
    val contentType: MediaType?
) {
    fun isEmpty() : Boolean = content == null && filePath == null
}

data class ResponseBodyData(
    val buffer: Buffer,
    val contentLength: Long,
    val contentType: MediaType?,
    val contentDisposition: ContentDisposition?
) {
    val shouldShowInline: Boolean
        get() = contentDisposition?.filename() == null && contentLength < BODY_THRESHOLD

    companion object {
        private const val BODY_THRESHOLD = ConnektInterceptorBase.BODY_THRESHOLD
    }
}
