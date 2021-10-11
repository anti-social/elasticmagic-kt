package dev.evo.elasticmagic.doc

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

abstract class BaseKotlinxDateTimeType<V> : BaseDateTimeType<V>() {
    protected fun parse(v: Any): Instant {
        return try {
            val dt = parseDateWithOptionalTime(v.toString())
            val timeZone = if (dt.tz.isNotEmpty()) {
                TimeZone.of(dt.tz)
            } else {
                TimeZone.UTC
            }
            LocalDateTime(
                dt.year,
                dt.month,
                dt.day,
                dt.hour,
                dt.minute,
                dt.second,
                dt.ms,
            ).toInstant(timeZone)
        } catch (ex: ValueDeserializationException) {
            if (v is Number) {
                Instant.fromEpochMilliseconds(v.toLong())
            } else {
                throw ex
            }
        }
    }

    override fun serializeTerm(v: V) = serialize(v)
}

object InstantType : BaseKotlinxDateTimeType<Instant>() {
    override val dateTypeName = "Instant"

    override fun serialize(v: Instant) = v.toString()

    override fun deserialize(v: Any, valueFactory: (() -> Instant)?): Instant = parse(v)
}

object DateTimeType : BaseKotlinxDateTimeType<LocalDateTime>() {
    override val dateTypeName = "LocalDateTime"

    override fun serialize(v: LocalDateTime): Any {
        return v.toInstant(TimeZone.UTC).toString()
    }

    override fun deserialize(v: Any, valueFactory: (() -> LocalDateTime)?): LocalDateTime {
        return parse(v).toLocalDateTime(TimeZone.UTC)
    }

}

object DateType : BaseKotlinxDateTimeType<LocalDate>() {
    override val name = "date"

    override val dateTypeName = "LocalDate"

    override fun serialize(v: LocalDate) = v.toString()

    override fun deserialize(v: Any, valueFactory: (() -> LocalDate)?): LocalDate {
        return parse(v).toLocalDateTime(TimeZone.UTC).date
    }
}
