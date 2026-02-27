package io.amplicode.connekt.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class DaemonCodecTest {

    @Test
    fun `encode PongResponse produces single-line JSON with no embedded newlines`() {
        val encoded = DaemonCodec.encode(PongResponse(requestId = "id-1", uptimeMs = 5000L))
        assertFalse(encoded.contains('\n'), "Encoded JSON must not contain newlines")
        assertFalse(encoded.contains('\r'), "Encoded JSON must not contain carriage returns")
        // Must contain expected field values
        assert(encoded.contains("\"id-1\"")) { "requestId not found in: $encoded" }
        assert(encoded.contains("5000")) { "uptimeMs not found in: $encoded" }
        assert(encoded.contains("\"pong\"")) { "type discriminator not found in: $encoded" }
    }

    @Test
    fun `decode CompileRequest from valid JSON`() {
        val line = """{"type":"compile","requestId":"x","scriptPath":"/a.kts","checksum":"abc"}"""
        val result = DaemonCodec.decode(line)
        assertIs<CompileRequest>(result)
        assertEquals("x", result.requestId)
        assertEquals("/a.kts", result.scriptPath)
        assertEquals("abc", result.checksum)
    }

    @Test
    fun `decode returns null for malformed JSON`() {
        assertNull(DaemonCodec.decode("not json at all"))
        assertNull(DaemonCodec.decode(""))
        assertNull(DaemonCodec.decode("{broken"))
    }

    @Test
    fun `decode returns null for unknown type`() {
        assertNull(DaemonCodec.decode("""{"type":"unknown_type","requestId":"x"}"""))
    }

    @Test
    fun `decode returns null when type field is missing`() {
        assertNull(DaemonCodec.decode("""{"requestId":"x","scriptPath":"/a.kts"}"""))
    }

    @Test
    fun `decode handles all known request types`() {
        assertIs<RunRequest>(
            DaemonCodec.decode("""{"type":"run","requestId":"r1","scriptPath":"/b.kts","checksum":"def"}""")
        )
        assertIs<CancelRequest>(
            DaemonCodec.decode("""{"type":"cancel","requestId":"r2"}""")
        )
        assertIs<PingRequest>(
            DaemonCodec.decode("""{"type":"ping","requestId":"r3"}""")
        )
        assertIs<ShutdownRequest>(
            DaemonCodec.decode("""{"type":"shutdown","requestId":"r4"}""")
        )
    }

    @Test
    fun `decodeType returns the type field value`() {
        assertEquals("ping", DaemonCodec.decodeType("""{"type":"ping","requestId":"x"}"""))
        assertEquals("compile", DaemonCodec.decodeType("""{"type":"compile","requestId":"x","scriptPath":"/a.kts","checksum":"abc"}"""))
    }

    @Test
    fun `decodeType returns null for malformed JSON`() {
        assertNull(DaemonCodec.decodeType("not json"))
        assertNull(DaemonCodec.decodeType("""{"requestId":"x"}"""))
    }
}
