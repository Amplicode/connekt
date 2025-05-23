package io.amplicode.connekt.dsl

import okhttp3.MediaType.Companion.toMediaType
import java.io.File

@ConnektDsl
class MultipartBodyBuilder(private val boundary: String) {
    private val parts = mutableListOf<MultipartBody.Part>()

    fun part(name: String? = null, contentType: String? = null, block: PartBuilder.() -> Unit) {
        parts.add(PartBuilder(contentType).apply {
            if (name != null) {
                contentDisposition(args = listOf("name" to name))
            }
        }.apply(block).build())
    }

    fun file(name: String, fileName: String, file: File) {
        part {
            contentDisposition(args = listOf("name" to name, "filename" to fileName))
            body(file.readBytes())
        }
    }

    internal fun build(): MultipartBody = MultipartBody(parts, boundary)

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

        fun body(body: String) {
            this.body = StringBody(body)
        }

        fun body(body: ByteArray) {
            this.body = ByteArrayBody(body)
        }

        fun formDataBody(block: FormDataBodyBuilder.() -> Unit) {
            this.body = FormDataBodyBuilder().apply(block).build()
        }

        fun header(name: String, value: Any) {
            headers.add(name to value)
        }

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