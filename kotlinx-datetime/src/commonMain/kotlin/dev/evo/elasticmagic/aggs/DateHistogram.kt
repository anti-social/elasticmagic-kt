package dev.evo.elasticmagic.aggs

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun DateHistogramBucket.keyAsDatetime(timeZone: TimeZone): LocalDateTime {
    return Instant.fromEpochMilliseconds(key).toLocalDateTime(timeZone)
}
