package io.amplicode.connekt.utils.okhttp

import okhttp3.MediaType


fun MediaType.hasSubTypeOf(subtype: String) =
    this.subtype.equals(subtype, ignoreCase = true)

fun MediaType.hasTypeOf(type: String) =
    this.type.equals(type, ignoreCase = true)

fun MediaType.fileExtension() = when (this.toString().lowercase()) {
    "text/plain" -> "txt"
    "text/html" -> "html"
    "text/css" -> "css"
    "application/json" -> "json"
    "application/xml" -> "xml"
    "application/pdf" -> "pdf"
    "application/zip" -> "zip"
    "application/gzip" -> "gz"
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "audio/mpeg" -> "mp3"
    "video/mp4" -> "mp4"
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
    "application/msword" -> "doc"
    "application/vnd.ms-excel" -> "xls"
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
    else -> this.subtype.takeIf { it.isValidFileExtension() }
}

private fun String.isValidFileExtension() =
    this.matches(Regex("^[a-zA-Z0-9]+$"))