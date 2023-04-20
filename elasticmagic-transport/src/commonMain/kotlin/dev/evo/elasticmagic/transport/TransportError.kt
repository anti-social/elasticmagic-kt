package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.DeserializationException
import dev.evo.elasticmagic.serde.forEach
import dev.evo.elasticmagic.serde.toMap
import kotlin.reflect.KFunction1

data class ErrorReason(
    val type: String,
    val reason: String,
    val resourceId: String?,
    val resourceType: String?,
    val script: String?,
    val scriptStack: List<String>,
    val causedBy: CausedBy?,
) {
    companion object {
        fun parse(data: Deserializer.ObjectCtx): ErrorReason? {
            val type = data.stringOrNull("type") ?: return null
            val reason = data.stringOrNull("reason") ?: return null
            val resourceId = data.stringOrNull("resource.id")
            val resourceType = data.stringOrNull("resource.type")
            val script = data.stringOrNull("script")
            val scriptStack = buildList {
                data.arrayOrNull("script_stack")?.forEach { v ->
                    add(v.toString())
                }
            }
            val causedBy = data.objOrNull("caused_by")?.run(CausedBy::parse)
            return ErrorReason(
                type = type,
                reason = reason,
                resourceId = resourceId,
                resourceType = resourceType,
                script = script,
                scriptStack = scriptStack,
                causedBy = causedBy,
            )
        }
    }
}

data class CausedBy(
    val type: String,
    val reason: String,
) {
    companion object {
        fun parse(data: Deserializer.ObjectCtx): CausedBy? {
            val type = data.stringOrNull("type") ?: return null
            val reason = data.stringOrNull("reason") ?: return null
            return CausedBy(
                type = type,
                reason = reason,
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
        val line: Int? = null,
        val col: Int? = null,
        val phase: String? = null,
        val grouped: Boolean? = null,
        val rootCauses: List<ErrorCause> = emptyList(),
        val failedShards: List<FailedShard> = emptyList(),
        val causedBy: ErrorReason? = null
    ) : TransportError() {
        companion object {
            @Suppress("NestedBlockDepth")
            fun parse(data: Deserializer.ObjectCtx): Structured? {
                val cause = ErrorCause.parse(data) ?: return null
                val phase = data.stringOrNull("phase")
                val grouped = data.booleanOrNull("grouped")
                val rootCausesIter = data.arrayOrNull("root_cause")?.iterator()
                val rootCauses = parseArray(rootCausesIter, ErrorCause::parse)

                val failedShardsIter = data.arrayOrNull("failed_shards")?.iterator()
                val failedShards = parseArray(failedShardsIter, FailedShard::parse)
                val causedBy = data.objOrNull("caused_by")?.run(ErrorReason::parse)
                return Structured(
                    type = cause.type,
                    reason = cause.reason,
                    line = cause.line,
                    col = cause.col,
                    phase = phase,
                    grouped = grouped,
                    rootCauses = rootCauses,
                    failedShards = failedShards,
                    causedBy = causedBy,
                )
            }

            private fun <T> parseArray(
                iterator: Deserializer.ArrayIterator?,
                parser: KFunction1<Deserializer.ObjectCtx, T?>
            ): List<T> {
                val result = mutableListOf<T>()
                if (iterator != null) {
                    while (iterator.hasNext()) {
                        iterator.objOrNull()?.also { objCtx ->
                            parser(objCtx)?.also(result::add)
                        }
                    }
                }
                return result
            }
        }
    }

    data class Simple(
        val error: String,
        val obj: Deserializer.ObjectCtx? = null,
    ) : TransportError()

    companion object {
        fun parse(error: String, deserializer: Deserializer): TransportError {
            // TODO: Implement parsing failures for delete and update by query APIs
            try {
                val jsonError = deserializer.objFromString(error)
                val errorObj = jsonError.objOrNull("error")
                if (errorObj != null) {
                    val structuredError = Structured.parse(errorObj)
                    if (structuredError != null) {
                        return structuredError
                    }
                }

                return when (val errorData = jsonError.anyOrNull("error")) {
                    null -> Simple(error)
                    is Deserializer.ObjectCtx -> Simple(errorData.toMap().toString(), errorData)
                    else -> Simple(errorData.toString())
                }
            } catch (e: DeserializationException) {
                return Simple(error)
            }

        }
    }
}
