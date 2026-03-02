package io.amplicode.connekt.execution

import io.amplicode.connekt.cli.AbstractConnektCommand
import io.amplicode.connekt.cli.createEnvStore
import java.io.File
import java.nio.file.Files
import kotlin.reflect.KProperty
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvTest {

    @Test
    fun `private env overrides public env`() {
        val tempDir = Files.createTempDirectory("connekt-test")

        val envFile = tempDir.resolve("connekt.env.json").toFile()
        envFile.writeText("""{"dev": {"API_KEY": "public-key", "PUBLIC_ONLY": "public-value"}}""")

        val privateEnvFile = tempDir.resolve("connekt.private.env.json").toFile()
        privateEnvFile.writeText("""{"dev": {"API_KEY": "private-key", "PRIVATE_ONLY": "private-value"}}""")

        val command = StubConnektCommand(
            envFile = envFile,
            privateEnvFile = privateEnvFile,
            envName = "dev",
            envParams = emptyList()
        )

        val envStore = createEnvStore(command)

        assertEquals("private-key", envStore.getValue(null, TestProperty("API_KEY")))
        assertEquals("public-value", envStore.getValue(null, TestProperty("PUBLIC_ONLY")))
        assertEquals("private-value", envStore.getValue(null, TestProperty("PRIVATE_ONLY")))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `envParams override both private and public env`() {
        val tempDir = Files.createTempDirectory("connekt-test")

        val envFile = tempDir.resolve("connekt.env.json").toFile()
        envFile.writeText("""{"dev": {"API_KEY": "public-key"}}""")

        val privateEnvFile = tempDir.resolve("connekt.private.env.json").toFile()
        privateEnvFile.writeText("""{"dev": {"API_KEY": "private-key"}}""")

        val command = StubConnektCommand(
            envFile = envFile,
            privateEnvFile = privateEnvFile,
            envName = "dev",
            envParams = listOf("API_KEY" to "param-key")
        )

        val envStore = createEnvStore(command)

        assertEquals("param-key", envStore.getValue(null, TestProperty("API_KEY")))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `works without private env file`() {
        val tempDir = Files.createTempDirectory("connekt-test")

        val scriptFile = tempDir.resolve("test.connekt.kts").toFile()
        scriptFile.writeText("")

        val envFile = tempDir.resolve("connekt.env.json").toFile()
        envFile.writeText("""{"dev": {"API_KEY": "public-key"}}""")

        val command = StubConnektCommand(
            script = scriptFile,
            envFile = envFile,
            envName = "dev"
        )

        val envStore = createEnvStore(command)

        assertEquals("public-key", envStore.getValue(null, TestProperty("API_KEY")))

        tempDir.toFile().deleteRecursively()
    }
}

class StubConnektCommand(
    override val script: File? = null,
    override val envFile: File? = null,
    override val privateEnvFile: File? = null,
    override val envName: String? = null,
    override val envParams: List<Pair<String, String>> = emptyList()
) : AbstractConnektCommand() {
    override fun run() {}
}

private class TestProperty(
    override val name: String,
    override val isLateinit: Boolean = false,
    override val isConst: Boolean = false
) : KProperty<String> {
    override val annotations: List<Annotation> = emptyList()
    override val isAbstract: Boolean = false
    override val isFinal: Boolean = true
    override val isOpen: Boolean = false
    override val isSuspend: Boolean = false
    override val parameters: List<kotlin.reflect.KParameter> = emptyList()
    override val returnType: kotlin.reflect.KType = kotlin.reflect.typeOf<String>()
    override val typeParameters: List<kotlin.reflect.KTypeParameter> = emptyList()
    override val visibility: kotlin.reflect.KVisibility = kotlin.reflect.KVisibility.PUBLIC
    override val getter: KProperty.Getter<String>
        get() = throw UnsupportedOperationException()

    override fun call(vararg args: Any?): String = throw UnsupportedOperationException()
    override fun callBy(args: Map<kotlin.reflect.KParameter, Any?>): String = throw UnsupportedOperationException()
}