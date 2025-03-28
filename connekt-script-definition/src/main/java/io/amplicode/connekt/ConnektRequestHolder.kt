package io.amplicode.connekt

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap
import kotlin.collections.set

/**
 * Provides controls to handle response data.
 *
 * @param T execution result type
 */
sealed class ConnektRequestHolder<T>() : Executable<T>() {

    protected val listeners = mutableListOf<ExecutionListener<T>>()

    protected fun fireResult(result: T) {
        listeners.forEach { listener ->
            listener.onResultObtained(result)
        }
    }

    final override fun execute(): T {
        val result = doExecute()
        fireResult(result)
        return result
    }

    protected abstract fun doExecute(): T

    fun addExecutionListener(listener: ExecutionListener<T>) {
        listeners.add(listener)
    }
}

class RequestHolder(
    private val connektRequest: ConnektRequest
) : ConnektRequestHolder<Response>() {

    private var mapper: MappedRequestHolder<*>? = null

    override fun doExecute(): Response {
        return connektRequest.execute()
    }

    /**
     * Provides controls to handle response
     */
    infix fun <R> then(mapFunction: MapFunction<Response, R>): MappedRequestHolder<R> {
        val mappedRequestHolder = MappedRequestHolder(
            this,
            mapFunction
        )
        mapper = mappedRequestHolder
        return mappedRequestHolder
    }
}

/**
 * @param R mapped type
 */
class MappedRequestHolder<R>(
    private val originRequestHolder: ConnektRequestHolder<Response>,
    private val responseMapper: MapFunction<Response, R>
) : ConnektRequestHolder<R>() {

    private var response: Response? = null
    private var result: R? = null

    init {
        originRequestHolder.onResultObtained(::handleResponse)
    }

    private fun handleResponse(obtainedResponse: Response) {
        response = obtainedResponse
        val newResult = responseMapper(obtainedResponse)
        result = newResult
        fireResult(newResult)
    }

    override fun doExecute(): R {
        result?.let { return it }

        // Request is not yet executed
        if (response == null) {
            originRequestHolder.execute()
            RequestExecutor.ignoreOnExecutionPhase(originRequestHolder)
        }

        // Expect not to be null once the request is executed
        return requireNotNull(result)
    }
}

typealias MapFunction<L, R> = L.() -> R

interface ExecutionListener<T> {
    fun onResultObtained(result: T)
}

fun <T> ConnektRequestHolder<T>.onResultObtained(handleResult: (T) -> Unit) {
    this.addExecutionListener(object : ExecutionListener<T> {
        override fun onResultObtained(result: T) {
            handleResult(result)
        }
    })
}
