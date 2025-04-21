package io.amplicode.connekt.test.utils.components

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

    override fun contains(property: KProperty<*>): Boolean {
        return property.name in env
    }
}