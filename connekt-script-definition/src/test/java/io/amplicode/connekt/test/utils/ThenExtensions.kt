package io.amplicode.connekt.test.utils

import io.amplicode.connekt.RequestHolder
import okhttp3.Response

fun RequestHolder.thenBodyString(handleString: (String) -> Unit = { }) =
    then {
        val bodyString = body!!.string()
        handleString(bodyString)
        bodyString
    }

fun RequestHolder.thenBodyInt() =
    then { body!!.string().toInt() }

fun Response.asString() = this.body!!.string()
fun Response.asInt() = this.body!!.string().toInt()