package io.amplicode.connekt

import io.amplicode.connekt.context.persistence.JavaSerializationFilePersistenceStore
import io.amplicode.connekt.context.persistence.PersistenceStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.NotSerializableException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JavaSerializationFilePersistenceStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var store: PersistenceStore

    @BeforeEach
    fun setUp() {
        store = JavaSerializationFilePersistenceStore(tempDir)
    }

    @AfterEach
    fun tearDown() {
        if (::store.isInitialized) {
            try {
                store.close()
            } catch (e: Exception) {
                // Ignore exceptions during teardown
            }
        }
    }

    @Test
    fun `test directory creation`() {
        // Create a new directory path that doesn't exist
        val newDir = tempDir.resolve("new-dir")
        assertFalse(Files.exists(newDir))

        // Create a store with that directory
        val newStore = JavaSerializationFilePersistenceStore(newDir)
        try {
            // Verify the directory was created
            assertTrue(Files.exists(newDir))
            assertTrue(Files.isDirectory(newDir))
        } finally {
            newStore.close()
        }
    }

    @Test
    fun `test map creation and retrieval`() {
        // Get a map and add some data
        val map = store.getMap("test-map")
        map["key1"] = "value1"
        map["key2"] = 42
        map["key3"] = listOf("a", "b", "c")

        // Verify the data is in the map
        assertEquals("value1", map["key1"])
        assertEquals(42, map["key2"])
        assertEquals(listOf("a", "b", "c"), map["key3"])

        // Get the same map again and verify the data is still there
        val sameMap = store.getMap("test-map")
        assertEquals("value1", sameMap["key1"])
        assertEquals(42, sameMap["key2"])
        assertEquals(listOf("a", "b", "c"), sameMap["key3"])

        // Get a different map and verify it's empty
        val differentMap = store.getMap("different-map")
        assertTrue(differentMap.isEmpty())
    }

    @Test
    fun `test persistence across store instances`() {
        // Get a map and add some data
        val map = store.getMap("persistent-map")
        map["key1"] = "value1"
        map["key2"] = 42

        // Close the store to ensure data is saved
        store.close()

        // Create a new store instance with the same directory
        val newStore = JavaSerializationFilePersistenceStore(tempDir)
        try {
            // Get the same map and verify the data is still there
            val persistentMap = newStore.getMap("persistent-map")
            assertEquals("value1", persistentMap["key1"])
            assertEquals(42, persistentMap["key2"])
        } finally {
            newStore.close()
        }
    }

    @Test
    fun `test handling of non-serializable objects`() {
        // Get a map
        val map = store.getMap("non-serializable-map")

        // Add a non-serializable object
        val nonSerializable = object {}
        map["non-serializable"] = nonSerializable

        // Close the store to trigger serialization - this should throw NotSerializableException
        assertThrows<NotSerializableException> {
            store.close()
        }

        // Since the previous store.close() failed, we need to create a new store without closing the previous one
        val newStore = JavaSerializationFilePersistenceStore(tempDir)
        try {
            // Get the map and verify it's empty (since no data was saved due to serialization failure)
            val recoveredMap = newStore.getMap("non-serializable-map")
            assertTrue(recoveredMap.isEmpty())
        } finally {
            newStore.close()
        }
    }

    @Test
    fun `test handling of complex serializable objects`() {
        // Get a map
        val map = store.getMap("complex-map")

        // Add a complex serializable object
        val complex = ComplexSerializable("test", 42, listOf("a", "b", "c"))
        map["complex"] = complex

        // Close the store to trigger serialization
        store.close()

        // Create a new store instance
        val newStore = JavaSerializationFilePersistenceStore(tempDir)
        try {
            // Get the map and verify the complex object was properly serialized and deserialized
            val recoveredMap = newStore.getMap("complex-map")
            val recoveredComplex = recoveredMap["complex"] as ComplexSerializable
            assertEquals("test", recoveredComplex.name)
            assertEquals(42, recoveredComplex.value)
            assertEquals(listOf("a", "b", "c"), recoveredComplex.items)
        } finally {
            newStore.close()
        }
    }

    @Test
    fun `test multiple maps`() {
        // Create and populate multiple maps
        val map1 = store.getMap("map1")
        map1["key1"] = "value1"

        val map2 = store.getMap("map2")
        map2["key2"] = "value2"

        val map3 = store.getMap("map3")
        map3["key3"] = "value3"

        // Close the store to trigger serialization
        store.close()

        // Create a new store instance
        val newStore = JavaSerializationFilePersistenceStore(tempDir)
        try {
            // Verify all maps were properly saved and loaded
            val recoveredMap1 = newStore.getMap("map1")
            assertEquals("value1", recoveredMap1["key1"])

            val recoveredMap2 = newStore.getMap("map2")
            assertEquals("value2", recoveredMap2["key2"])

            val recoveredMap3 = newStore.getMap("map3")
            assertEquals("value3", recoveredMap3["key3"])
        } finally {
            newStore.close()
        }
    }

    // A complex serializable class for testing
    data class ComplexSerializable(
        val name: String,
        val value: Int,
        val items: List<String>
    ) : Serializable
}
