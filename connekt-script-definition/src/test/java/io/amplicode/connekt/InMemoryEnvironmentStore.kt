package io.amplicode.connekt

import io.amplicode.connekt.context.EnvironmentStore
import kotlin.reflect.KProperty

class InMemoryEnvironmentStore : EnvironmentStore {

    private val env = mutableMapOf<String, Any?>()

    operator fun set(key: String, value: Any?) {
        env[key] = value
    }

    override fun <T> getValue(
        receiver: Any?,
        property: KProperty<*>
    ): T {
        return env[property.name] as T
    }
}