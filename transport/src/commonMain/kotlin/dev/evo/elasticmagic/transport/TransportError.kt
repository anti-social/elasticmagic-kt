package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.toMap

data class ErrorReason(
    val type: String,
    val reason: String,
    val resourceId: String?,
    val resourceType: String?,
) {
    companion object {
        fun parse(data: Deserializer.ObjectCtx): ErrorReason? {
            val type = data.stringOrNull("type") ?: return null
            val reason = data.stringOrNull("reason") ?: return null
            val resourceId = data.stringOrNull("resource.id")
            val resourceType = data.stringOrNull("resource.type")
            return ErrorReason(
                type = type,
                reason = reason,
                resourceId = resourceId,
                resourceType = resourceType,
            )
        }
    }
}

data class ErrorCause(
    val type: String,
    val reason: String,
    val resourceId: String?,
    val resourceType: String?,
    val line: Int?,
    val col: Int?,
) {
    companion object {
        fun parse(data: Deserializer.ObjectCtx): ErrorCause? {
            val reason = ErrorReason.parse(data) ?: return null
            val line = data.intOrNull("line")
            val col = data.intOrNull("col")
            return ErrorCause(
                type = reason.type,
                reason = reason.reason,
                resourceId = reason.resourceId,
                resourceType = reason.resourceType,
                line = line,
                col = col,
            )
        }
    }
}

data class FailedShard(
    val shard: Int,
    val index: String,
    val node: String,
    val reason: ErrorReason,
) {
    companion object {
        fun parse(data: Deserializer.ObjectCtx): FailedShard? {
            val shard = data.intOrNull("shard") ?: return null
            val index = data.stringOrNull("index") ?: return null
            val node = data.stringOrNull("node") ?: return null
            val reasonData = data.objOrNull("reason") ?: return null
            val reason = ErrorReason.parse(reasonData) ?: return null
            return FailedShard(
                shard = shard,
                index = index,
                node = node,
                reason = reason,
            )
        }
    }
}

sealed class TransportError {
    data class Structured(
        val type: String,
        val reason: String,
        val line: Int?,
        val col: Int?,
        val phase: String?,
        val grouped: Boolean?,
        val rootCauses: List<ErrorCause>,
        val failedShards: List<FailedShard>,
    ) : TransportError() {
        companion object {
            fun parse(data: Deserializer.ObjectCtx): Structured? {
                val cause = ErrorCause.parse(data) ?: return null
                val phase = data.stringOrNull("phase")
                val grouped = data.booleanOrNull("grouped")
                val rootCausesData = data.arrayOrNull("root_cause")
                val rootCauses = mutableListOf<ErrorCause>()
                if (rootCausesData != null) {
                    while (rootCausesData.hasNext()) {
                        val rootCauseData = rootCausesData.objOrNull()
                        if (rootCauseData != null) {
                            val rootCause = ErrorCause.parse(rootCauseData)
                            if (rootCause != null) {
                                rootCauses.add(rootCause)
                            }
                        }
                    }
                }
                val failedShardsData = data.arrayOrNull("failed_shards")
                val failedShards = mutableListOf<FailedShard>()
                if (failedShardsData != null) {
                    while (failedShardsData.hasNext()) {
                        val failedShardData = failedShardsData.objOrNull()
                        if (failedShardData != null) {
                            FailedShard.parse(failedShardData)?.also {
                                failedShards.add(it)
                            }
                        }
                    }
                }
                return Structured(
                    type = cause.type,
                    reason = cause.reason,
                    line = cause.line,
                    col = cause.col,
                    phase = phase,
                    grouped = grouped,
                    rootCauses = rootCauses,
                    failedShards = failedShards,
                )
            }
        }
    }

    data class Simple(val error: String) : TransportError()

    companion object {
        fun parse(data: Deserializer.ObjectCtx): TransportError {
            val errorData = data.objOrNull("error")
            if (errorData != null) {
                val error = Structured.parse(errorData)
                if (error != null) {
                    return error
                }
            }
            val error = data.stringOrNull("error")
            if (error != null) {
                return Simple(error)
            }
            return Simple(data.toMap().toString())
        }
    }
}
