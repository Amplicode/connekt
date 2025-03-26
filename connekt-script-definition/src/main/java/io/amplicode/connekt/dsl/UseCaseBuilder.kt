package io.amplicode.connekt.dsl

import io.amplicode.connekt.ConnektContext
import io.amplicode.connekt.ConnektRequest
import io.amplicode.connekt.ConnektRequestHolder
import io.amplicode.connekt.Executable
import io.amplicode.connekt.Request
import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.RequestExecutor
import io.amplicode.connekt.RequestPath
import io.amplicode.connekt.RequestHolder
import kotlin.reflect.KProperty

class UseCaseBuilder(private val connektContext: ConnektContext) {
    @RequestBuilderCall
    @Request("GET")
    @Suppress("FunctionName")
    fun GET(
        @RequestPath path: String,
        configure: GetBuilder.() -> Unit = {}
    ) = addRequest {
        GetBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("POST")
    @Suppress("FunctionName")
    fun POST(
        @RequestPath path: String,
        configure: PostBuilder.() -> Unit = {}
    ) = addRequest {
        PostBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("PUT")
    @Suppress("FunctionName")
    fun PUT(
        @RequestPath path: String,
        configure: PutBuilder.() -> Unit = {}
    ) = addRequest {
        PutBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("OPTIONS")
    @Suppress("FunctionName")
    fun OPTIONS(
        @RequestPath path: String,
        configure: OptionsBuilder.() -> Unit = {}
    ) = addRequest {
        OptionsBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("PATCH")
    @Suppress("FunctionName")
    fun PATCH(
        @RequestPath path: String,
        configure: PatchBuilder.() -> Unit = {}
    ) = addRequest {
        PatchBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("DELETE")
    @Suppress("FunctionName")
    fun DELETE(
        @RequestPath path: String,
        configure: DeleteBuilder.() -> Unit = {}
    ) = addRequest {
        DeleteBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("HEAD")
    @Suppress("FunctionName")
    fun HEAD(
        @RequestPath path: String,
        configure: HeadBuilder.() -> Unit = {}
    ) = addRequest {
        HeadBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("TRACE")
    @Suppress("FunctionName")
    fun TRACE(
        @RequestPath path: String,
        configure: TraceBuilder.() -> Unit = {}
    ) = addRequest {
        TraceBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Suppress("unused")
    fun request(
        method: String,
        @RequestPath path: String,
        configure: BaseRequestBuilder.() -> Unit = {}
    ) = addRequest {
        BaseRequestBuilder(method, path).apply(configure)
    }

    private fun <T : BaseRequestBuilder> addRequest(requestBuilderSupplier: () -> T): RequestHolder {
        val connektRequest = ConnektRequest(
            connektContext,
            requestBuilderSupplier
        )
        val requestHolder = RequestHolder(connektRequest)
        requestsQueue.add(requestHolder)
        return requestHolder
    }

    operator fun <R> ConnektRequestHolder<R>.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): RequestDelegate<R> {
        return RequestDelegate(this)
    }

    private val requestsQueue =
        mutableListOf<Executable<*>>()

    fun executeRequests() {
        requestsQueue.forEach { request ->
            RequestExecutor.execute(request)
        }
    }
}

class RequestDelegate<T>(private val requestHolder: ConnektRequestHolder<T>) {
    operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T {
        return getValueImpl()
    }

    operator fun getValue(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): T {
        return getValueImpl()
    }

    private fun getValueImpl(): T {
        return requestHolder.execute()
    }
}