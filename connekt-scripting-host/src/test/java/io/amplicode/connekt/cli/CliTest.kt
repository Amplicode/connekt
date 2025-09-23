package io.amplicode.connekt.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals

class CliTest {

    @Test
    fun `run non-existent file`() {
        val result = ConnektCommand()
            .test("--script non-existent-script.connekt.kts")
        assertEquals(1, result.statusCode)
        assertEquals(
            """
                Usage: Connekt [<options>]

                Error: invalid value for --script: file "non-existent-script.connekt.kts" does not exist.
                
                """.trimIndent(),
            result.stderr
        )
    }
}