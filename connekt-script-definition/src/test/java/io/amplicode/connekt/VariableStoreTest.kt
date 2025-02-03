/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import org.mapdb.DBMaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariableStoreTest {

    @Test
    fun simpleTest() {
        val db = DBMaker.memoryDB().make()
        val varStore = VariablesStore(db)

        val oneAsStr by varStore.string()
        oneAsStr.set("one")
        assertEquals("one", oneAsStr.get())

        val oneAsInt by varStore.int()
        // The value must become `null` because the variable type had been changed
        // so the variable can't be rad as Int now
        assertNull(oneAsInt.get())
        oneAsInt.set(1)
        assertEquals(1, oneAsInt.get())
    }
}