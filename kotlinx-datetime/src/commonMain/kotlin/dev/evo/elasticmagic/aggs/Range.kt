package dev.evo.elasticmagic.aggs

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun DateRangeBucket.fromAsDatetime(timeZone: TimeZone): LocalDateTime? {
    return from?.let {
        Instant.fromEpochMilliseconds(it.toLong()).toLocalDateTime(timeZone)
    }
}

fun DateRangeBucket.toAsDatetime(timeZone: TimeZone): LocalDateTime? {
    return to?.let {
        Instant.fromEpochMilliseconds(it.toLong()).toLocalDateTime(timeZone)
    }
}
