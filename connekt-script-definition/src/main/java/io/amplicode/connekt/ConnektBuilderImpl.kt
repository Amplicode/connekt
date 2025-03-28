package io.amplicode.connekt

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import io.amplicode.connekt.client.ClientConfigurer
import io.amplicode.connekt.console.println
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.PersistentRequestDelegate
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.dsl.UseCaseBuilder
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap
import kotlin.reflect.KProperty

fun ConnektBuilder(context: ConnektContext): ConnektBuilder = ConnektBuilderImpl(context)

internal class ConnektBuilderImpl(
    private val context: ConnektContext
) : ConnektBuilder {
    override val env = context.env
    override val vars = context.vars

    override fun <T> variable(): DelegateProvider<T> {
        return DelegateProvider(context.values)
    }

    override fun configureClient(configure: ClientConfigurer) {
        context.globalClientConfigurer = configure
    }

    @RequestBuilderCall
    override fun useCase(
        name: String?,
        build: UseCaseBuilder.() -> Unit
    ) {
        val useCaseBuilder = UseCaseBuilder(context)

        context.addRequest(
            object : Executable<Unit>() {
                override fun execute() {
                    context.printer.println("Running flow [${name}]")
                    useCaseBuilder.build()
                    useCaseBuilder.executeRequests()
                }
            }
        )
    }

    private val jsonPaths: WeakHashMap<Response, ReadContext> = WeakHashMap()

    override fun Response.jsonPath(): ReadContext {
        var readContext = jsonPaths[this]

        if (readContext == null) {
            val stream = ByteArrayOutputStream()
            body?.source()?.buffer?.copyTo(stream)

            readContext = JsonPath.parse(
                ByteArrayInputStream(stream.toByteArray()),
                Configuration.builder()
                    .jsonProvider(JacksonJsonProvider(context.objectMapper))
                    .mappingProvider(JacksonMappingProvider(context.objectMapper))
                    .build()
            )

            jsonPaths[this] = readContext

            return readContext
        }

        return readContext
    }

    fun ReadContext.readString(path: String): String {
        return read(path)
    }

    fun ReadContext.readInt(path: String): Int {
        return read(path)
    }

    fun ReadContext.readLong(path: String): Long {
        return read(path)
    }

    fun <T> ReadContext.readList(path: String, clazz: Class<T>): List<T> {
        val nodes: List<JsonNode> = read(path, object : TypeRef<List<JsonNode>>() {})
        return nodes.map {
            context.objectMapper.treeToValue(it, clazz)
        }
    }

    override fun request(
        method: String,
        path: String,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val connektRequest = ConnektRequest(context) {
            RequestBuilder(method, path, context).apply(configure)
        }
        val thenable = RequestHolder(connektRequest)
        context.addRequest(thenable)
        return thenable
    }

    override operator fun <R> ConnektRequestHolder<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): PersistentRequestDelegate<R> {
        return PersistentRequestDelegate(context, this, prop.name)
    }
}