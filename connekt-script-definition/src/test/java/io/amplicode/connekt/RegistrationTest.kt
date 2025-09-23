package io.amplicode.connekt

import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.context.execution.ExecutionContext
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.ScriptStatement
import io.amplicode.connekt.test.utils.server.TestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RegistrationTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `test registrations`() {
        val scriptStatement = ScriptStatement()
        scriptStatement.applyScript {
            GET("$host/foo", "foo")
            GET("$host/bar", "bar")
            val delegated by GET("$host/foo")
            val delegatedWithExplicitName by GET(
                "$host/foo",
                "explicit-name"
            )
            val delegatedWithThen by GET("$host/foo").then {
                body!!.string()
            }
        }
        val executionContext = scriptStatement.context.executionContext
        val registrations = executionContext
            .registrations
            .map {
                it.coordinates
            }
            .flatten()
            .toSet()
        assertEquals(
            setOf(
                DeclarationCoordinates(0),
                DeclarationCoordinates(1),
                DeclarationCoordinates(2),
                DeclarationCoordinates(3),
                DeclarationCoordinates(4),
                DeclarationCoordinates("foo"),
                DeclarationCoordinates("bar"),
                DeclarationCoordinates("delegated"),
                DeclarationCoordinates("delegatedWithExplicitName"),
                DeclarationCoordinates("explicit-name"),
                DeclarationCoordinates("delegatedWithThen"),
            ),
            registrations
        )
        executionContext.apply {
            assertRegistrationFound(DeclarationCoordinates("foo"))
            assertRegistrationFound(DeclarationCoordinates("delegatedWithThen"))
        }
    }

    private fun ExecutionContext.assertRegistrationFound(declarationCoordinates: DeclarationCoordinates) {
        val registration = this.findRegistration(declarationCoordinates)
        assertNotNull(registration)
    }
}