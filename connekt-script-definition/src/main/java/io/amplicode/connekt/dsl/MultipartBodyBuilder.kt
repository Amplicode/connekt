package io.amplicode.connekt.dsl

import okhttp3.MediaType.Companion.toMediaType
import java.io.File

/**
 * Builder for constructing `multipart/\*` request bodies.
 *
 * Use this builder inside a `multipart {}` block to add individual parts, each of which can
 * carry its own headers, content type, and body. Parts can represent form fields, uploaded files,
 * or arbitrary binary/text content.
 *
 * Example usage:
 * ```
 * multipart {
 *     part(name = "metadata", contentType = "application/json") {
 *         body("""{"key": "value"}""")
 *     }
 *     file(name = "upload", fileName = "report.pdf", file = File("/tmp/report.pdf"))
 * }
 * ```
 *
 * @param boundary The MIME boundary string used to delimit parts in the multipart body.
 */
@ConnektDsl
class MultipartBodyBuilder(private val boundary: String) {
    private val parts = mutableListOf<MultipartBody.Part>()

    /**
     * Adds a multipart part configured via the [block] lambda.
     *
     * The [block] receives a [PartBuilder] which allows setting headers, content disposition,
     * and the body of the part. The optional [name] parameter sets the `Content-Disposition`
     * `name` directive automatically; the optional [contentType] sets the part's media type.
     *
     * ```kotlin
     * part(name = "metadata", contentType = "application/json") {
     *     body("""{"description": "profile picture"}""")
     *     header("Content-Encoding", "utf-8")
     * }
     * ```
     *
     * @param name Optional form-data field name. When provided, a `Content-Disposition` header
     *   with `name="<value>"` is added automatically.
     * @param contentType Optional media type string for this part (e.g. `"application/json"`).
     * @param block Lambda applied to the [PartBuilder] to configure the part.
     * @see PartBuilder
     */
    fun part(name: String? = null, contentType: String? = null, block: PartBuilder.() -> Unit) {
        parts.add(PartBuilder(contentType).apply {
            if (name != null) {
                contentDisposition(args = listOf("name" to name))
            }
        }.apply(block).build())
    }

    /**
     * Adds a file as a multipart part using `form-data` content disposition.
     *
     * This is a convenience wrapper around [part] that reads the file bytes and sets the
     * appropriate `Content-Disposition` header with both `name` and `filename` directives.
     *
     * @param name The form-data field name for the file part.
     * @param fileName The filename reported in the `Content-Disposition` header.
     * @param file The [File] whose contents will be sent as the part body.
     */
    fun file(name: String, fileName: String, file: File) {
        part {
            contentDisposition(args = listOf("name" to name, "filename" to fileName))
            body(file.readBytes())
        }
    }

    internal fun build(): MultipartBody = MultipartBody(parts, boundary)

    /**
     * Builder for an individual part within a [MultipartBodyBuilder].
     *
     * Provides methods to set the body, add headers, and configure the `Content-Disposition`
     * header of a single multipart part. A body must be set exactly once before the part is built.
     *
     * @param contentType Optional media type string for this part (e.g. `"text/plain"`).
     */
    @ConnektDsl
    class PartBuilder(private val contentType: String? = null) {
        private var body: RequestBody? = null
            set(value) {
                require(field == null) {
                    "Body already set"
                }

                field = value
            }
        private val headers = mutableListOf<Pair<String, Any>>()

        /**
         * Sets the body of this part to the given plain-text [body] string.
         *
         * @param body The text content to use as the part body.
         * @throws IllegalArgumentException if a body has already been set on this part.
         */
        fun body(body: String) {
            this.body = StringBody(body)
        }

        /**
         * Sets the body of this part to the given raw byte array [body].
         *
         * @param body The binary content to use as the part body.
         * @throws IllegalArgumentException if a body has already been set on this part.
         */
        fun body(body: ByteArray) {
            this.body = ByteArrayBody(body)
        }

        /**
         * Sets the body of this part to a URL-encoded form-data body built via [block].
         *
         * This is a convenience method for embedding `application/x-www-form-urlencoded` content
         * inside a multipart part.
         *
         * ```kotlin
         * part(contentType = "application/x-www-form-urlencoded") {
         *     formDataBody {
         *         field("name", "alice")
         *         field("role", "admin")
         *     }
         * }
         * ```
         *
         * @param block Lambda applied to a [FormDataBodyBuilder] to define the form fields.
         * @throws IllegalArgumentException if a body has already been set on this part.
         * @see FormDataBodyBuilder
         */
        fun formDataBody(block: FormDataBodyBuilder.() -> Unit) {
            this.body = FormDataBodyBuilder().apply(block).build()
        }

        /**
         * Adds a header with the given [name] and [value] to this part.
         *
         * @param name The header field name (e.g. `"Content-Type"`).
         * @param value The header field value. Converted to a string during serialization.
         */
        fun header(name: String, value: Any) {
            headers.add(name to value)
        }

        /**
         * Sets or replaces the `Content-Disposition` header for this part.
         *
         * Any previously added `Content-Disposition` header is removed before the new one is set.
         * The header value is constructed as `"<value>; <key1>=\"<val1>\"; <key2>=\"<val2>\"..."`.
         *
         * @param value The disposition type, defaults to `"form-data"`.
         * @param args A list of key-value pairs appended as parameters to the disposition value
         *   (e.g. `listOf("name" to "file", "filename" to "report.pdf")`).
         */
        fun contentDisposition(value: String = "form-data", args: List<Pair<String, String>>) {
            headers.removeIf { it.first == "Content-Disposition" }
            header(
                "Content-Disposition",
                "$value; " + args.joinToString(separator = ";") { it.first + "=" + "\"" + it.second + "\"" })
        }

        internal fun build(): MultipartBody.Part {
            val body = body
            require(body != null) {
                "Body is mandatory"
            }
            return MultipartBody.Part(body, headers, contentType?.toMediaType())
        }
    }
}
