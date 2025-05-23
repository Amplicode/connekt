package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadFileTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `download file`() {
        val (responseFilesDir, context) = TestEnvironment()

        runScript(context = context) {
            GET("$host/download") {
                queryParam("filename", "my-file.txt")
                queryParam("length", 500)
            }
        }

        val files = Files.list(responseFilesDir)
            .asSequence()
            .toList()

        assertTrue { files.size == 1 }
        assertEquals(
            500,
            files.first().readText().length
        )
    }

    @Test
    fun `test unique file names`() {
        val responseFilesDir = createTempDirectory("connekt-file-test")

        // Download the same file multiple times
        repeat(3) {
            val (_, context) = TestEnvironment(responseFilesDir)
            runScript(context = context) {
                GET("$host/download") {
                    queryParam("filename", "same-file.txt")
                    queryParam("length", 100)
                }
            }
        }

        // Check that we have 3 files with unique names
        val files = Files.list(responseFilesDir)
            .asSequence()
            .toList()

        assertEquals(
            3,
            files.size,
            "Should have created 3 files with unique names"
        )

        // Check that all files have different names
        val fileNames = files.map { it.fileName.toString() }
        assertEquals(
            3,
            fileNames.distinct().size,
            "All file names should be unique"
        )

        // Check that all files contain the expected content
        files.forEach { file ->
            val content = String(Files.readAllBytes(file))
            assertEquals(
                100,
                content.length,
                "File should have the expected content length"
            )
        }
    }

    @Test
    fun `test store filename`() {
        val (responseFilesDir, context) = TestEnvironment()

        runScript(context = context) {
            GET("$host/download") {
                queryParam("filename", "my-file.zip")
                queryParam("length", 200)
            }
        }

        val responseFile= Files.list(responseFilesDir)
            .asSequence()
            .toList()
            .single()

        assertEquals(
            "my-file.zip",
            responseFile.fileName.toString()
        )
    }

    private data class TestEnvironment(
        val responseFilesDir: Path = createTempDirectory("connekt-file-test"),
        val context: ConnektContext = testConnektContext(responseStorageDir = responseFilesDir)
    )
}