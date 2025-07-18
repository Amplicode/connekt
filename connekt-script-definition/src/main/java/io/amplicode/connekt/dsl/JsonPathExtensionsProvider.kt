package io.amplicode.connekt.dsl

import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import okhttp3.Response
import org.intellij.lang.annotations.Language

fun ReadContext.readString(@Language("JSONPath") path: String): String {
    return read(path)
}

inline fun <reified T> ReadContext.doRead(@Language("JSONPath") path: String): T {
    return read(path, object : TypeRef<T>() {})
}

fun ReadContext.readInt(@Language("JSONPath") path: String): Int {
    return read(path)
}

fun ReadContext.readLong(@Language("JSONPath") path: String): Long {
    return read(path)
}

fun ReadContext.readBoolean(@Language("JSONPath") path: String): Boolean {
    return read(path)
}

interface JsonPathExtensionsProvider {
    fun Response.jsonPath(): ReadContext
    fun <T> ReadContext.readList(
        @Language("JSONPath") path: String,
        clazz: Class<T>
    ): List<T>
}