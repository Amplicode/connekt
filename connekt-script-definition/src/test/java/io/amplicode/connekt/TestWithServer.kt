package io.amplicode.connekt

import io.amplicode.connekt.test.utils.TestServer
import io.amplicode.connekt.test.utils.TestServerParamResolver
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerParamResolver::class)
abstract class TestWithServer(server: TestServer) {
    protected val host: String = server.host
}