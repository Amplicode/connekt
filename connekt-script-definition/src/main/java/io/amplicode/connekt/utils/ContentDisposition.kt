package io.amplicode.connekt.utils

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
fun ContentDisposition.filename() = parameters["filename"]?.takeIf { it.isNotBlank() }
