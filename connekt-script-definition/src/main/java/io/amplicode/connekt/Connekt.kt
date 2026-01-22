/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.doRead
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "connekt.kts",
    compilationConfiguration = ConnektConfiguration::class
)
abstract class Connekt(private val connektBuilder: ConnektBuilder) : ConnektBuilder by connektBuilder {
    inline fun <reified T> okhttp3.Response.decode(path: String = "$"): T {
        return jsonPath().doRead<T>(path)
    }
}
