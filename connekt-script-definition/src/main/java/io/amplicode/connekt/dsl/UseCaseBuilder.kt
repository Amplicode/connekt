package io.amplicode.connekt.dsl

import com.jayway.jsonpath.ReadContext
import io.amplicode.connekt.*
import io.amplicode.connekt.context.ConnektContext
import okhttp3.Response
import kotlin.reflect.KProperty

@ConnektDsl
class UseCaseBuilder(private val context: ConnektContext) {

    @RequestBuilderCall
    @Request("GET")
    @Suppress("FunctionName")
    fun GET(
        @RequestPath path: String,
        configure: GetBuilder.() -> Unit = {}
    ) = addRequest {
        GetBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("POST")
    @Suppress("FunctionName")
    fun POST(
        @RequestPath path: String,
        configure: PostBuilder.() -> Unit = {}
    ) = addRequest {
        PostBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("PUT")
    @Suppress("FunctionName")
    fun PUT(
        @RequestPath path: String,
        configure: PutBuilder.() -> Unit = {}
    ) = addRequest {
        PutBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("OPTIONS")
    @Suppress("FunctionName")
    fun OPTIONS(
        @RequestPath path: String,
        configure: OptionsBuilder.() -> Unit = {}
    ) = addRequest {
        OptionsBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("PATCH")
    @Suppress("FunctionName")
    fun PATCH(
        @RequestPath path: String,
        configure: PatchBuilder.() -> Unit = {}
    ) = addRequest {
        PatchBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("DELETE")
    @Suppress("FunctionName")
    fun DELETE(
        @RequestPath path: String,
        configure: DeleteBuilder.() -> Unit = {}
    ) = addRequest {
        DeleteBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("HEAD")
    @Suppress("FunctionName")
    fun HEAD(
        @RequestPath path: String,
        configure: HeadBuilder.() -> Unit = {}
    ) = addRequest {
        HeadBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Request("TRACE")
    @Suppress("FunctionName")
    fun TRACE(
        @RequestPath path: String,
        configure: TraceBuilder.() -> Unit = {}
    ) = addRequest {
        TraceBuilder(path, context).apply(configure)
    }

    @RequestBuilderCall
    @Suppress("unused")
    fun request(
        method: String,
        @RequestPath path: String,
        configure: RequestBuilder.() -> Unit = {}
    ) = addRequest {
        RequestBuilder(method, path, context).apply(configure)
    }

    private fun <T : RequestBuilder> addRequest(requestBuilderSupplier: () -> T): Response {
        val executionStrategy = DefaultExecutionStrategy(context)
        val requestBuilder = requestBuilderSupplier()
        return executionStrategy.execute(requestBuilder)
    }

    operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): UseCaseRequestDelegate<T> {
        return UseCaseRequestDelegate(this)
    }

    infix fun <T> Response.then(handle: Response.() -> T): T = run(handle)

    fun Response.jsonPath(): ReadContext = context.jsonContext.getReadContext(this)
}

class UseCaseRequestDelegate<T>(private val value: T) : RequestDelegate<T> {
    override operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T {
        return getValueImpl()
    }

    override operator fun getValue(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): T {
        return getValueImpl()
    }

    private fun getValueImpl(): T = value
}
