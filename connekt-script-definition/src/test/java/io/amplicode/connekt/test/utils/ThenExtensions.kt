package io.amplicode.connekt.test.utils

import io.amplicode.connekt.RequestHolder

fun RequestHolder.thenBodyString(handleString: (String) -> Unit = { }) =
    then {
        val bodyString = body!!.string()
        handleString(bodyString)
        bodyString
    }

fun RequestHolder.thenBodyInt() =
    then { body!!.string().toInt() }