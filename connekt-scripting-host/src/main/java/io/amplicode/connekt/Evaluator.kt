package io.amplicode.connekt

import com.github.ajalt.clikt.core.main
import io.amplicode.connekt.cli.ConnektCommand

fun main(vararg args: String) = ConnektCommand().main(args)