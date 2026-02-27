package io.amplicode.connekt.daemon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codec for the daemon JSON-Lines protocol.
 *
 * - [encode] serialises a [DaemonResponse] to a single-line JSON string (no trailing newline).
 * - [decode] parses a raw JSON line and returns the matching [DaemonRequest] subtype, or `null`
 *   for malformed / unknown input.
 * - [decodeType] reads only the `"type"` field efficiently, intended for use in the dispatch loop.
 */
object DaemonCodec {

    private val json = Json {
        // Do not fail on unknown keys sent by the plugin
        ignoreUnknownKeys = true
        // Encode default values so the `type` field is always present in output
        encodeDefaults = true
    }

    /**
     * Serialises [response] to a compact, single-line JSON string.
     * Each subtype is serialised directly with its own serializer so that its `type` property
     * (a regular field with a default value) is included naturally. This avoids the conflict
     * between kotlinx-serialization's class discriminator mechanism and the explicit `type`
     * property declared on each response class.
     * The caller is responsible for appending a newline when writing to a stream.
     */
    fun encode(response: DaemonResponse): String = when (response) {
        is CompileResultResponse -> json.encodeToString(CompileResultResponse.serializer(), response)
        is OutputChunkResponse -> json.encodeToString(OutputChunkResponse.serializer(), response)
        is RunCompleteResponse -> json.encodeToString(RunCompleteResponse.serializer(), response)
        is PongResponse -> json.encodeToString(PongResponse.serializer(), response)
        is ShutdownAckResponse -> json.encodeToString(ShutdownAckResponse.serializer(), response)
        is ErrorResponse -> json.encodeToString(ErrorResponse.serializer(), response)
    }

    /**
     * Parses [line] as a JSON object, reads the `"type"` field, and deserialises the payload
     * into the appropriate [DaemonRequest] subtype.
     *
     * Returns `null` when:
     * - [line] is not valid JSON
     * - the `"type"` field is absent or has an unrecognised value
     * - required fields for the target type are missing
     */
    fun decode(line: String): DaemonRequest? {
        val obj = parseJsonObject(line) ?: return null
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        return try {
            when (type) {
                "compile" -> json.decodeFromJsonElement(CompileRequest.serializer(), obj)
                "run" -> json.decodeFromJsonElement(RunRequest.serializer(), obj)
                "cancel" -> json.decodeFromJsonElement(CancelRequest.serializer(), obj)
                "ping" -> json.decodeFromJsonElement(PingRequest.serializer(), obj)
                "shutdown" -> json.decodeFromJsonElement(ShutdownRequest.serializer(), obj)
                else -> null
            }
        } catch (_: Exception) {
            null
        }//{"type":"run", "requestId": "bar", "scriptPath": "/Users/alexander/workspace/connekt/google_calendar.connekt.kts" }
    }

    /**
     * Returns the value of the `"type"` field from [line] without fully deserialising the message.
     * Returns `null` if [line] is not valid JSON or the field is absent.
     */
    fun decodeType(line: String): String? {
        val obj = parseJsonObject(line) ?: return null
        return obj["type"]?.jsonPrimitive?.content
    }

    /**
     * Returns the value of the `"requestId"` field from [line] without fully deserialising the message.
     * Returns `null` if [line] is not valid JSON or the field is absent.
     */
    fun decodeRequestId(line: String): String? {
        val obj = parseJsonObject(line) ?: return null
        return obj["requestId"]?.jsonPrimitive?.content
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun parseJsonObject(line: String): JsonObject? =
        try {
            json.parseToJsonElement(line).jsonObject
        } catch (_: Exception) {
            null
        }
}
