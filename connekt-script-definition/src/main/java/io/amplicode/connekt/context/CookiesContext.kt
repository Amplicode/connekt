package io.amplicode.connekt.context

import org.mapdb.DB
import org.mapdb.Serializer

class CookiesContext(private val db: DB) {
    val cookies by lazy {
        db
            .hashMap("cookies", Serializer.STRING, Serializer.STRING)
            .createOrOpen()
    }
}