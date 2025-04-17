package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadFileTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `download file`() {
        val tempDirectory = createTempDirectory("connekt-upload-file-test")

        runScript(
            context = testConnektContext(
                responseStorageDir = tempDirectory,
            )
        ) {
            GET("$host/download") {
                queryParam("filename", "my-file.txt")
                queryParam("length", 500)
            }
        }

        val files = Files.list(tempDirectory)
            .asSequence()
            .toList()

        assertTrue { files.size == 1 }
        assertEquals(
            500,
            files.first().readText().length
        )
    }
}