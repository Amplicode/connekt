package io.amplicode.connekt.context

/**
 * Interface for managing lifecycle event subscriptions within the Connekt system.
 *
 * Implementations of this interface allow components to register listeners
 * that will be notified of lifecycle events, such as startup, shutdown, or other custom transitions.
 */
interface ConnektLifeCycleCallbacks {

    /**
     * Registers the given [listener] to receive lifecycle event callbacks.
     *
     * @param listener An instance of [ConnektLifeCycleListener] to be notified of lifecycle events.
     */
    fun subscribe(listener: ConnektLifeCycleListener)
}

/**
 * Listener interface for receiving Connekt lifecycle events.
 *
 * Implement this interface to be notified when the Connekt component is closed or shut down.
 */
interface ConnektLifeCycleListener {

    /**
     * Called when the Connekt component has been closed.
     *
     * This method is invoked to perform cleanup or respond to shutdown events.
     */
    fun onClosed()
}

fun ConnektLifeCycleCallbacks.onClose(onCloseOperation: () -> Unit) {
    subscribe(object : ConnektLifeCycleListener {
        override fun onClosed() {
            onCloseOperation()
        }
    })
}
class ConnektLifeCycleCallbacksImpl : ConnektLifeCycleCallbacks {
    private val listeners = mutableListOf<ConnektLifeCycleListener>()

    override fun subscribe(listener: ConnektLifeCycleListener) {
        listeners += listener
    }

    fun fireClosed() {
        listeners.forEach { it.onClosed() }
    }
}
