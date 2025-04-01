package io.amplicode.connekt

import io.amplicode.connekt.dsl.*
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.runScript
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPathTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `do complete check`() {
        runScript() {
            POST("$host/echo-body") {
                contentType("application/json")
                //language=json
                body("""
                {
                  "id": 1,
                  "name": "Test User",
                  "email": "test@example.com",
                  "isActive": true,
                  "roles": ["admin", "user"],
                  "profile": {
                    "age": 30,
                    "city": "New York"
                  },
                  "createdAt": "2025-03-31T12:00:00Z"
                }
                """.trimIndent())
            } then {
                jsonPath().apply {
                    assertEquals(
                        "Test User",
                        readString("name")
                    )
                    assertEquals(
                        1,
                        readInt("id")
                    )
                    assertEquals(
                        listOf("admin", "user"),
                        readList("roles", String::class.java)
                    )
                    assertEquals(
                        true,
                        readBoolean("isActive")
                    )

                    // Nested object //

                    assertEquals(
                        30,
                        readInt("profile.age")
                    )
                    assertEquals(
                        "New York",
                        readString("profile.city")
                    )
                }
            }
        }
    }
}