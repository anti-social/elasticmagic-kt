package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.types.BaseDateTimeType
import dev.evo.elasticmagic.types.ValueDeserializationException
import dev.evo.elasticmagic.types.deErr
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

abstract class BaseKotlinxDateTimeType<V> : BaseDateTimeType<V>() {
    protected fun parse(v: Any): Instant {
        val err = try {
            val dt = parseDateWithOptionalTime(v.toString())
            val timeZone = if (dt.tz.isNotEmpty()) {
                TimeZone.of(dt.tz)
            } else {
                TimeZone.UTC
            }
            return LocalDateTime(
                dt.year,
                dt.month,
                dt.day,
                dt.hour,
                dt.minute,
                dt.second,
                dt.ms,
            ).toInstant(timeZone)
        } catch (ex: IllegalArgumentException) {
            ex
        } catch (ex: ValueDeserializationException) {
            ex
        }

        if (v is Number) {
            // Elasticsearch parses number 2015 as year
            // but 20150 as milliseconds from epoch
            return Instant.fromEpochMilliseconds(v.toLong())
        }
        if (err !is ValueDeserializationException) {
            deErr(v, termType.simpleName ?: "<unknown>", err)
        }
        throw err
    }

    override fun serializeTerm(v: V & Any) = serialize(v)
}

object InstantType : BaseKotlinxDateTimeType<Instant>() {
    override val termType = Instant::class

    override fun serialize(v: Instant) = v.toString()

    override fun deserialize(v: Any, valueFactory: (() -> Instant)?): Instant = parse(v)
}

object DateTimeType : BaseKotlinxDateTimeType<LocalDateTime>() {
    override val termType = LocalDateTime::class

    override fun serialize(v: LocalDateTime): Any {
        return v.toInstant(TimeZone.UTC).toString()
    }

    override fun deserialize(v: Any, valueFactory: (() -> LocalDateTime)?): LocalDateTime {
        return parse(v).toLocalDateTime(TimeZone.UTC)
    }

}

object DateType : BaseKotlinxDateTimeType<LocalDate>() {
    override val termType = LocalDate::class

    override fun serialize(v: LocalDate) = v.toString()

    override fun deserialize(v: Any, valueFactory: (() -> LocalDate)?): LocalDate {
        return parse(v).toLocalDateTime(TimeZone.UTC).date
    }
}
