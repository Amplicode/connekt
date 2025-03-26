package io.amplicode.connekt.test.utils

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class TestServerParamResolver : ParameterResolver {

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == TestServer::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any? {
        val serverResource = extensionContext.root
            .getStore(Namespace.GLOBAL)
            .getOrComputeIfAbsent(
                "test-server",
                { _ ->
                    TestServerResource(TestServer())
                },
                TestServerResource::class.java
            )
        return serverResource.testServer
    }
}

class TestServerResource(val testServer: TestServer) : CloseableResource {
    override fun close() {
        testServer.stop()
    }
}