package io.amplicode.connekt.context

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap

class JsonContext() {
    val objectMapper: ObjectMapper = defaultObjectMapper

    private val jsonPaths: WeakHashMap<Response, ReadContext> = WeakHashMap()

    fun getReadContext(response: Response): ReadContext {
        var readContext = jsonPaths[response]

        if (readContext == null) {
            val stream = ByteArrayOutputStream()
            response.body?.source()?.buffer?.copyTo(stream)

            readContext = JsonPath.parse(
                ByteArrayInputStream(stream.toByteArray()),
                Configuration.builder()
                    .jsonProvider(JacksonJsonProvider(objectMapper))
                    .mappingProvider(JacksonMappingProvider(objectMapper))
                    .build()
            )

            jsonPaths[response] = readContext

            return readContext
        }

        return readContext
    }
}

val defaultObjectMapper: ObjectMapper by lazy {
    ObjectMapper()
        .registerModules(kotlinModule())
        .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
        .enable(JsonParser.Feature.IGNORE_UNDEFINED)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}