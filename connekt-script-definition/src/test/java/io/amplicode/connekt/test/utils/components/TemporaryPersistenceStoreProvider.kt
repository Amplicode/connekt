package io.amplicode.connekt.test.utils.components

import io.amplicode.connekt.context.persistence.JavaSerializationFilePersistenceStore
import io.amplicode.connekt.context.persistence.PersistenceStore
import io.amplicode.connekt.context.persistence.defaultPersistenceStore
import kotlin.io.path.createTempDirectory

/**
 * A factory for [PersistenceStore].
 * Each store uses the same temporary storage dir.
 */
class TemporaryPersistenceStoreProvider {
    val tempDir = createTempDirectory("connekt-test-dir")
    fun getPersistenceStore(): PersistenceStore = defaultPersistenceStore(tempDir)
}