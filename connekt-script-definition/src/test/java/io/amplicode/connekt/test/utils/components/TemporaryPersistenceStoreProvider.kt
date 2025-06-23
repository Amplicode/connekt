package io.amplicode.connekt.test.utils.components

import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.context.persistence.defaultStorage
import kotlin.io.path.createTempDirectory

/**
 * A factory for [Storage].
 * Each store uses the same temporary storage dir.
 */
class TemporaryPersistenceStoreProvider {
    val tempDir = createTempDirectory("connekt-test-dir")
    fun getPersistenceStore(): Storage = defaultStorage(tempDir)
}