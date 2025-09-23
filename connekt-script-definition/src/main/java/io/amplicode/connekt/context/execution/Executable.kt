package io.amplicode.connekt.context.execution

abstract class Executable<T> {
    internal abstract fun execute(): T
}