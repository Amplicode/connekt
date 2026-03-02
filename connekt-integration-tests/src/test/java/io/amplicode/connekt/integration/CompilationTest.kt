package io.amplicode.connekt.integration

import org.junit.jupiter.api.Test

/**
 * Verifies that all fixture scripts compile successfully without executing them.
 * These tests run without a server — no HTTP requests are made.
 */
class CompilationTest {

    private fun compile(scriptName: String) {
        val file = CompilationTest::class.java.classLoader
            .getResource("scripts/$scriptName")!!
            .let { java.io.File(it.toURI()) }
        compileScriptFile(file).assertSuccess()
    }

    @Test
    fun `get_foo compiles`() = compile("basic/get_foo.connekt.kts")

    @Test
    fun `echo_query_params compiles`() = compile("basic/echo_query_params.connekt.kts")

    @Test
    fun `echo_body_post compiles`() = compile("basic/echo_body_post.connekt.kts")

    @Test
    fun `echo_form_params compiles`() = compile("form/echo_form_params.connekt.kts")

    @Test
    fun `use_case compiles`() = compile("use_case/use_case.connekt.kts")

    @Test
    fun `delegated_vars compiles`() = compile("delegated_vars/delegated_vars.connekt.kts")

    @Test
    fun `import_helper compiles`() {
        val tempDir = kotlin.io.path.createTempDirectory("connekt-compile-test").toFile()
        try {
            listOf("second_level_import.connekt.kts", "helper_utils.connekt.kts", "import_helper.connekt.kts").forEach { name ->
                val src = CompilationTest::class.java.classLoader.getResourceAsStream("scripts/import/$name")!!
                tempDir.resolve(name).writeBytes(src.readBytes())
            }
            compileScriptFile(tempDir.resolve("import_helper.connekt.kts")).assertSuccess()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
