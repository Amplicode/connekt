/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

abstract class Executable<T> {
    // TODO make it `Unit`
    internal abstract fun execute(): T
}
