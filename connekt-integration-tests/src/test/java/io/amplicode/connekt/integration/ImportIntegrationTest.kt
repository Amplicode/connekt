package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ImportIntegrationTest : IntegrationTest() {

    /** Creates a temporary directory that is deleted on JVM exit. */
    private fun tempDir(): File {
        return Files.createTempDirectory("connekt-import-integration-test").toFile().also {
            it.deleteOnExit()
        }
    }

    /** Copies a classpath resource script into [dir] and returns the resulting [File]. */
    private fun copyScriptFromResources(dir: File, name: String): File {
        val resource = ImportIntegrationTest::class.java.classLoader.getResourceAsStream("scripts/import/$name")
            ?: error("Script resource not found: scripts/import/$name")
        val dest = dir.resolve(name)
        resource.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        dest.deleteOnExit()
        return dest
    }

    /** Copies all import scripts into [dir]. */
    private fun prepareDir(vararg names: String): Pair<File, Map<String, File>> {
        val dir = tempDir()
        val files = names.associateWith { copyScriptFromResources(dir, it) }
        return dir to files
    }

    // -------------------------------------------------------------------------
    // Nested imports: A imports B, B imports C — C's utilities are available in A
    // -------------------------------------------------------------------------

    @Test
    fun `nested import - function from level 3 is available in level 1 via transitive import`() {
        // import_helper → helper_utils → second_level_import
        // buildEchoUrl (helper_utils) uses encodeQueryParam (second_level_import) internally
        val (_, files) = prepareDir("second_level_import.connekt.kts", "helper_utils.connekt.kts", "import_helper.connekt.kts")

        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(files["import_helper.connekt.kts"]!!, context).assertSuccess()
    }

    // -------------------------------------------------------------------------
    // Transitive property access: root script uses a value defined in level 3
    // directly (not via a function from level 2)
    // -------------------------------------------------------------------------

    @Test
    @Disabled
    fun `nested import - property from level 3 is directly accessible in level 1`() {
        // transitive_root → transitive_helper → transitive_const
        // transitive_root uses urlSuffix (from transitive_const) directly, not through a function
        val (_, files) = prepareDir("transitive_const.connekt.kts", "transitive_helper.connekt.kts", "transitive_root.connekt.kts")

        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(files["transitive_root.connekt.kts"]!!, context).assertSuccess()
    }

    // -------------------------------------------------------------------------
    // Two imports in one file
    // -------------------------------------------------------------------------

    @Test
    fun `two imports - utilities from both helpers are available in main script`() {
        // two_imports.connekt.kts imports helper_utils AND extra_assert_utils
        val (_, files) = prepareDir(
            "second_level_import.connekt.kts",
            "helper_utils.connekt.kts",
            "extra_assert_utils.connekt.kts",
            "two_imports.connekt.kts"
        )

        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(files["two_imports.connekt.kts"]!!, context).assertSuccess()
    }

    // -------------------------------------------------------------------------
    // Auth in import + request numbering
    // -------------------------------------------------------------------------

    @Test
    fun `auth configured in import is applied to requests in main script and request numbers do not shift`() {
        val (_, files) = prepareDir("auth_config.connekt.kts", "auth_from_import.connekt.kts")
        val mainScript = files["auth_from_import.connekt.kts"]!!
        val context = createIntegrationContext(
            environmentStore = ValuesEnvironmentStore(mapOf("host" to host)),
            builderFactory = ::oauthAwareBuilderFactory
        )
        runScriptFile(mainScript, context).assertSuccess()
    }
}
