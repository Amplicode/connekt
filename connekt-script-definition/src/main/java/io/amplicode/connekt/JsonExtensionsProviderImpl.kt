package io.amplicode.connekt

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.JsonPathExtensionsProvider
import okhttp3.Response

class JsonExtensionsProviderImpl(private val context: ConnektContext) : JsonPathExtensionsProvider {

    override fun Response.jsonPath(): ReadContext {
        return context.jsonContext.getReadContext(this)
    }

    override fun <T> ReadContext.readList(path: String, clazz: Class<T>): List<T> {
        val nodes: List<JsonNode> = read(path, object : TypeRef<List<JsonNode>>() {})
        return nodes.map {
            context.jsonContext.objectMapper.treeToValue(it, clazz)
        }
    }
}