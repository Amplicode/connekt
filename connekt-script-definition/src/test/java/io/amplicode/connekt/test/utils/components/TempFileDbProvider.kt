package io.amplicode.connekt.test.utils.components

import org.mapdb.DBMaker
import kotlin.io.path.createTempFile

class TempFileDbProvider {
    val dbTempFile = createTempFile("connekt-test.db")
        .toFile()
        .also {
            it.delete()
            it.deleteOnExit()
        }

    fun getDb() = DBMaker.fileDB(dbTempFile).make()
}