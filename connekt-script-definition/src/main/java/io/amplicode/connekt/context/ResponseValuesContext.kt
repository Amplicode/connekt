package io.amplicode.connekt.context

import org.mapdb.DB
import org.mapdb.Serializer

class ResponseValuesContext(db: DB) {

    val values: MutableMap<String, Any?> =
        db.hashMap("values", Serializer.STRING, Serializer.JAVA).createOrOpen()
}