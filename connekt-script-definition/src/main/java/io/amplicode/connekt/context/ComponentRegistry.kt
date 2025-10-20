package io.amplicode.connekt.context

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.cast

interface ComponentRegistry :
    ComponentProvider,
    AutoCloseable {
    fun <T : Any> registerComponent(clazz: KClass<T>, instance: T)
}

interface ComponentProvider {
    fun <T : Any> getComponent(clazz: KClass<T>): T
}

class ComponentsRegistryImpl : ComponentRegistry {

    val components = mutableMapOf<KClass<*>, Any>()

    override fun <T : Any> registerComponent(clazz: KClass<T>, instance: T) {
        components[clazz] = instance
    }

    override fun <T : Any> getComponent(clazz: KClass<T>): T {
        val component = components[clazz]
        requireNotNull(component) {
            "No component of type $clazz registered."
        }
        return clazz.cast(component)
    }

    override fun close() {
        components.values.forEach {
            if (it is AutoCloseable) {
                try {
                    it.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

inline fun <reified T : Any> ComponentRegistry.registerComponent(instance: T) {
    registerComponent(T::class, instance)
}

inline fun <reified T : Any> ComponentProvider.getComponent(): T {
    return getComponent(T::class)
}

inline operator fun <reified T : Any> ComponentRegistry.provideDelegate(
    thisRef: Any?,
    property: KProperty<*>
): ComponentDelegateBase<T> {
    return object : ComponentDelegateBase<T>() {
        override fun getValueImpl(thisRef: Any?, property: KProperty<*>): T {
            return getComponent(T::class)
        }

        override fun setValueImpl(thisRef: Any?, property: KProperty<*>, value: T) {
            registerComponent(value)
        }
    }
}

abstract class ComponentDelegateBase<T> {

    operator fun getValue(
        thisRef: Nothing?,
        property: KProperty<*>
    ): T = getValueImpl(thisRef, property)

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): T = getValueImpl(thisRef, property)

    operator fun setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
    ) = setValueImpl(thisRef, property, value)

    operator fun setValue(
        @Suppress("unused") thisRef: Any?,
        @Suppress("unused") property: KProperty<*>,
        value: T
    ) = setValueImpl(thisRef, property, value)

    protected abstract fun getValueImpl(thisRef: Any?, property: KProperty<*>): T

    protected abstract fun setValueImpl(thisRef: Any?, property: KProperty<*>, value: T)
}