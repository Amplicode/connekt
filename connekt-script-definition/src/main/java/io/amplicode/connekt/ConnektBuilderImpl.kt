package io.amplicode.connekt

import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.StoredVariableDelegate
import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.context.execution.Executable
import io.amplicode.connekt.dsl.*
import java.time.Instant
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal class ConnektBuilderImpl(private val context: ConnektContext) :
    ConnektBuilder,
    JsonPathExtensionsProvider by context.jsonPathExtensionsProvider,
    AuthExtensions by context.authExtensions {

    override val env = context.environmentStore
    override val vars = context.variablesStore
    override fun variable(): StoredVariableDelegate = vars.variable()

    override fun configureClient(configure: ClientConfigurer) {
        context.clientContext.globalConfigurer = configure
    }

    override fun <T> useCase(name: String?, runUseCase: UseCaseBuilder.() -> T): UseCaseExecutable<T> {
        lateinit var useCaseExecutable: UseCaseExecutable<T>
        val useCase = object : UseCase<T> {
            override val name: String? = name
            override fun perform(useCaseBuilder: UseCaseBuilder): T {
                val result = useCaseBuilder.runUseCase()
                useCaseExecutable.captureTtl(useCaseBuilder.ttlDuration)
                return result
            }
        }
        useCaseExecutable = UseCaseExecutable(context, useCase)
        context.executionContext.registerExecutable(useCaseExecutable, name)
        return useCaseExecutable
    }

    override fun request(
        method: String,
        path: String,
        name: String?,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val requestBuilderProvider = RequestBuilderProvider {
            RequestBuilder(method, path, context).apply(configure)
        }
        val requestHolder = RequestHolder(requestBuilderProvider, context)
        context.executionContext.registerExecutable(requestHolder, name)
        return requestHolder
    }

    override operator fun <R> ExecutableWithResult<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val executable = this
        // Register this declaration with delegating property name
        registerExecutableWithPropName(prop, executable.originalExecutable)
        val storedValue = UpdatableStoredValue(prop, executable)
        return StoredValueDelegate(
            context,
            executable,
            storedValue::value,
            storedValue::expired
        )
    }

    override operator fun <R> UseCaseExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val executable = this
        // Register this declaration with delegating property name
        registerExecutableWithPropName(prop, executable)
        return UseCaseDelegate(prop, executable)
    }

    private fun registerExecutableWithPropName(prop: KProperty<*>, executable: Executable<*>) {
        val coordinates = DeclarationCoordinates(prop.name)
        context.executionContext.addCoordinatesForExecutable(coordinates, executable)
    }

    inner class UpdatableStoredValue<R>(
        private val prop: KProperty<*>,
        requestHolder: ExecutableWithResult<R>
    ) {
        private val key = prop.name
        private val storage = context.variablesStore
        private val ttlSource = requestHolder.originalExecutable as? RequestHolder

        fun expired(): Boolean = storage.isExpired(key)

        var value: R?
            get() = if (storage.isExpired(key)) null else storage.getValue(key, prop.returnType)
            set(value) {
                storage.setValue(key, value)
            }

        init {
            // update stored value and its expiration on response received
            requestHolder.onResultObtained<R> {
                value = it
                storage.setExpiration(key, ttlSource?.expiresAt)
            }
        }
    }

    inner class UseCaseDelegate<R>(
        private val prop: KProperty<*>,
        private val executable: UseCaseExecutable<R>,
    ) : ValueDelegateBase<R>() {
        private val key = prop.name
        private val storeMap = context.variablesStore

        init {
            executable.addListener {
                storeMap.setValue(key, it)
                storeMap.setExpiration(key, executable.expiresAt)
            }
        }

        override fun getValueImpl(
            thisRef: Any?,
            property: KProperty<*>
        ): R {
            val expired = storeMap.isExpired(key)
            if (!expired) {
                storeMap.getValue<R>(key, prop.returnType)?.let { return it }
            }
            val message = if (expired) {
                "Cached value for property `${property.name}` has expired, re-executing useCase"
            } else {
                "Initializing value for property `${property.name}`"
            }
            context.printer.println(message)
            return executable.execute()
        }
    }

}

interface UseCase<T> {
    val name: String?
    fun perform(useCaseBuilder: UseCaseBuilder): T
}

class UseCaseExecutable<T>(
    private val context: ConnektContext,
    private val useCase: UseCase<T>
) : Executable<T>() {

    private val listeners: MutableList<(T) -> Unit> = mutableListOf()

    private var capturedTtl: Duration? = null

    /**
     * Expiration timestamp of the cached value from the last execution, or `null` if no TTL was
     * configured for the useCase. Updated on every [execute].
     */
    var expiresAt: Instant? = null
        private set

    fun addListener(listener: (T) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Records the fixed TTL configured inside the useCase body. Called while the useCase runs, so
     * [execute] can turn it into an [expiresAt] once the strategy is known.
     */
    fun captureTtl(duration: Duration?) {
        capturedTtl = duration
    }

    override fun execute(): T {
        val executionStrategy = context.executionContext.getExecutionStrategy(this)
        capturedTtl = null
        val value = executionStrategy.executeUseCase(context, useCase)

        expiresAt = if (executionStrategy.performsRealRequest) {
            capturedTtl?.let { Instant.now().plus(it.toJavaDuration()) }
        } else {
            null
        }

        for (listener in listeners) {
            listener(value)
        }

        return value
    }
}
