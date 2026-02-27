package io.amplicode.connekt.daemon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Requests (plugin → daemon)
// ---------------------------------------------------------------------------

@Serializable
sealed class DaemonRequest {
    abstract val type: String
    abstract val requestId: String
}

@Serializable
@SerialName("compile")
data class CompileRequest(
    override val type: String = "compile",
    override val requestId: String,
    val scriptPath: String,
    val checksum: String,
    val compilationCache: Boolean? = null
) : DaemonRequest()

@Serializable
@SerialName("run")
data class RunRequest(
    override val type: String = "run",
    override val requestId: String,
    val scriptPath: String,
    val checksum: String,
    val envName: String? = null,
    val requestNumber: Int? = null,
    val requestName: String? = null
) : DaemonRequest()

@Serializable
@SerialName("cancel")
data class CancelRequest(
    override val type: String = "cancel",
    override val requestId: String
) : DaemonRequest()

@Serializable
@SerialName("ping")
data class PingRequest(
    override val type: String = "ping",
    override val requestId: String
) : DaemonRequest()

@Serializable
@SerialName("shutdown")
data class ShutdownRequest(
    override val type: String = "shutdown",
    override val requestId: String
) : DaemonRequest()

// ---------------------------------------------------------------------------
// Responses (daemon → plugin)
// ---------------------------------------------------------------------------

@Serializable
sealed class DaemonResponse {
    abstract val type: String
    abstract val requestId: String
}

@Serializable
@SerialName("compile_result")
data class CompileResultResponse(
    override val type: String = "compile_result",
    override val requestId: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val cached: Boolean
) : DaemonResponse()

@Serializable
@SerialName("output_chunk")
data class OutputChunkResponse(
    override val type: String = "output_chunk",
    override val requestId: String,
    val stream: String,
    val data: String
) : DaemonResponse()

@Serializable
@SerialName("run_complete")
data class RunCompleteResponse(
    override val type: String = "run_complete",
    override val requestId: String,
    val exitCode: Int
) : DaemonResponse()

@Serializable
@SerialName("pong")
data class PongResponse(
    override val type: String = "pong",
    override val requestId: String,
    val uptimeMs: Long
) : DaemonResponse()

@Serializable
@SerialName("shutdown_ack")
data class ShutdownAckResponse(
    override val type: String = "shutdown_ack",
    override val requestId: String
) : DaemonResponse()

@Serializable
@SerialName("error")
data class ErrorResponse(
    override val type: String = "error",
    override val requestId: String,
    val code: String,
    val message: String
) : DaemonResponse()
