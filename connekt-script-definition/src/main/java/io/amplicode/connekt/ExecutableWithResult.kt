package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.execution.Executable
import io.amplicode.connekt.dsl.RequestBuilder
import okhttp3.Response

/**
 * Provides controls to handle response data.
 *
 * @param T execution result type
 */
abstract class ExecutableWithResult<T>() : Executable<T>() {

    abstract val originalExecutable: Executable<*>

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
    val requestBuilderProvider: RequestBuilderProvider,
    val context: ConnektContext
) : ExecutableWithResult<Response>() {

    override val originalExecutable = this

    private val executionStrategy
        get() = context.executionContext.getExecutionStrategy(this)

    override fun doExecute(): Response {
        val requestBuilder = requestBuilderProvider.getRequestBuilder()
        val response = executionStrategy.executeRequest(context, requestBuilder)
        return response
    }

    /**
     * Provides controls to handle response
     */
    infix fun <R> then(mapFunction: MapFunction<Response, R>): MappedRequestHolder<R> {
        return executionStrategy.mapRequestExecutable(context, this, mapFunction)
    }
}

/**
 * @param R mapped type
 */
class MappedRequestHolder<R>(
    private val context: ConnektContext,
    private val originRequestHolder: ExecutableWithResult<Response>,
    private val mapFunction: MapFunction<Response, R>
) : ExecutableWithResult<R>() {

    override val originalExecutable = originRequestHolder

    private var response: Response? = null
    private var result: R? = null

    init {
        originRequestHolder.onResultObtained(::handleResponse)
    }

    private fun handleResponse(obtainedResponse: Response) {
        response = obtainedResponse
        val newResult = mapFunction(obtainedResponse)
        result = newResult
        fireResult(newResult)
    }

    override fun doExecute(): R {
        result?.let { return it }

        // Request is not yet executed
        if (response == null) {
            originRequestHolder.execute()
            context.executionContext.ignoreOnExecutionPhase(originRequestHolder)
        }

        // Expect not to be null once the request is executed
        return requireNotNull(result)
    }
}

typealias MapFunction<L, R> = L.() -> R

interface ExecutionListener<T> {
    fun onResultObtained(result: T)
}

fun <T> ExecutableWithResult<T>.onResultObtained(handleResult: (T) -> Unit) {
    this.addExecutionListener(object : ExecutionListener<T> {
        override fun onResultObtained(result: T) {
            handleResult(result)
        }
    })
}

fun interface RequestBuilderProvider {
    fun getRequestBuilder(): RequestBuilder
}