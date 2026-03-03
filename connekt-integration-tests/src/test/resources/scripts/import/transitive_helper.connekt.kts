@file:Import("transitive_const.connekt.kts")

fun buildTransitiveUrl(host: String): String = "$host/$urlSuffix"