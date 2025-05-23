package io.amplicode.connekt.test.utils

import java.util.UUID

fun extractBodyString(s: String): String = s.split("\n\n")
    .last()
    .removeSuffix("\n")

fun uuid() = UUID.randomUUID().toString()

fun Any?.asUnit() = Unit