package io.amplicode.connekt

import io.amplicode.connekt.context.VariablesStore
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.context.persistence.defaultStorage
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VariablesStoreExpirationTest {

    @Test
    fun `no stored expiration never expires - in memory`() = withStores { store ->
        store.setValue("token", "value")
        assertFalse(store.isExpired("token"))
    }

    @Test
    fun `future expiration is not expired`() = withStores { store ->
        store.setValue("token", "value")
        store.setExpiration("token", Instant.now().plusSeconds(3600))
        assertFalse(store.isExpired("token"))
    }

    @Test
    fun `past expiration is expired`() = withStores { store ->
        store.setValue("token", "value")
        store.setExpiration("token", Instant.now().minusSeconds(1))
        assertTrue(store.isExpired("token"))
    }

    @Test
    fun `null expiration clears a previously set one`() = withStores { store ->
        store.setValue("token", "value")
        store.setExpiration("token", Instant.now().minusSeconds(1))
        assertTrue(store.isExpired("token"))

        store.setExpiration("token", null)
        assertFalse(store.isExpired("token"))
    }

    /**
     * Runs [test] against both storage backends so the JSON-backed path (where a cleared expiration
     * is persisted as a JSON null node) is exercised alongside the in-memory one.
     */
    private fun withStores(test: (VariablesStore) -> Unit) {
        test(VariablesStore(InMemoryStorage()))

        val dir = createTempDirectory("connekt-ttl-store-test")
        val jsonStorage: Storage = defaultStorage(dir)
        jsonStorage.use { test(VariablesStore(it)) }
    }
}
