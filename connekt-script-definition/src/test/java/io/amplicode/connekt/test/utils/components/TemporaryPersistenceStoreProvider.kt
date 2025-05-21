package io.amplicode.connekt.test.utils.components

import io.amplicode.connekt.context.FilePersistenceStore
import io.amplicode.connekt.context.PersistenceStore
import kotlin.io.path.createTempDirectory

/**
 * A factory for [PersistenceStore].
 * Each store uses the same temporary storage dir.
 */
class TemporaryPersistenceStoreProvider {
    val tempDir = createTempDirectory("connekt-test-dir")
    fun getPersistenceStore(): PersistenceStore = FilePersistenceStore(tempDir)
}