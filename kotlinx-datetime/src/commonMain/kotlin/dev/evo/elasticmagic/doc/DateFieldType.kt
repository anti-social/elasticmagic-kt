package dev.evo.elasticmagic.doc

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

abstract class BaseDateTimeType<V> : FieldType<V> {
    override val name = "date"

    protected abstract val dateTypeName: String

    companion object {
        private val DATETIME_REGEX = Regex(
            // Date
            "(\\d{4})(?:-(\\d{2}))?(?:-(\\d{2}))?" +
            "(?:T" +
                // Time
                "(\\d{2})?(?::(\\d{2}))?(?::(\\d{2}))?(?:\\.(\\d{1,9}))?" +
                // Timezone
                "(?:Z|([+-]\\d{2}(?::?\\d{2})?))?" +
            ")?"
        )

    }

    override fun serializeTerm(v: Any?): Any = when (v) {
        is Instant -> v.toString()
        is LocalDateTime -> v.toInstant(TimeZone.UTC).toString()
        is LocalDate -> v.toString()
        is String -> v
        is Number -> v
        else -> serErr(v)
    }

    private fun fail(v: Any, cause: Throwable? = null): Nothing {
        throw ValueDeserializationException(v, dateTypeName, cause)
    }

    internal fun parse(v: Any): Instant {
        return try {
            parseDateWithOptionalTime(v.toString())
        } catch (ex: ValueDeserializationException) {
            if (v is Number) {
                Instant.fromEpochMilliseconds(v.toLong())
            } else {
                throw ex
            }
        }
    }

    @Suppress("MagicNumber")
    internal fun parseDateWithOptionalTime(v: String): Instant {
        val datetimeMatch = DATETIME_REGEX.matchEntire(v) ?: fail(v)
        val (year, month, day, hour, minute, second, msRaw, tz) = datetimeMatch.destructured
        val ms = when (msRaw.length) {
            0 -> msRaw
            in 1..2 -> msRaw.padEnd(3, '0')
            else -> msRaw.substring(0, 3)
        }
        val timeZone = if (tz.isNotEmpty()) {
            TimeZone.of(tz)
        } else {
            TimeZone.UTC
        }
        try {
            return LocalDateTime(
                year.toInt(),
                month.toIntIfNotEmpty(1),
                day.toIntIfNotEmpty(1),
                hour.toIntIfNotEmpty(0),
                minute.toIntIfNotEmpty(0),
                second.toIntIfNotEmpty(0),
                ms.toIntIfNotEmpty(0) * 1000_000
            )
                .toInstant(timeZone)
        } catch (ex: IllegalArgumentException) {
            fail(v, ex)
        }
    }

    private fun String.toIntIfNotEmpty(default: Int): Int {
        return if (isEmpty()) default else toInt()
    }
}

object InstantType : BaseDateTimeType<Instant>() {
    override val dateTypeName = "Instant"

    override fun serialize(v: Instant): Any {
        return v.toString()
    }

    override fun deserialize(v: Any, valueFactory: (() -> Instant)?): Instant = parse(v)
}

object DateTimeType : BaseDateTimeType<LocalDateTime>() {
    override val dateTypeName = "LocalDateTime"

    override fun serialize(v: LocalDateTime): Any {
        return v.toInstant(TimeZone.UTC).toString()
    }

    override fun deserialize(v: Any, valueFactory: (() -> LocalDateTime)?): LocalDateTime {
        return parse(v).toLocalDateTime(TimeZone.UTC)
    }
}

object DateType : BaseDateTimeType<LocalDate>() {
    override val name = "date"

    override val dateTypeName = "LocalDate"

    override fun serialize(v: LocalDate): Any {
        return v.toString()
    }

    override fun deserialize(v: Any, valueFactory: (() -> LocalDate)?): LocalDate {
        return parse(v).toLocalDateTime(TimeZone.UTC).date
    }
}
