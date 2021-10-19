package dev.evo.elasticmagic.doc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

import kotlin.test.Test

class DateFieldTypeTests {
    @Test
    fun instantSerialize() {
        InstantType.serialize(
            LocalDateTime(2015, 1, 1, 12, 10, 30).toInstant(TimeZone.UTC)
        ) shouldBe "2015-01-01T12:10:30Z"
        InstantType.serialize(
            LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000).toInstant(TimeZone.UTC)
        ) shouldBe "2015-01-01T12:10:30.001Z"
    }

    @Test
    fun instantDeserialize() {
        InstantType.deserialize(0) shouldBe LocalDateTime(1970, 1, 1, 0, 0, 0).toInstant(TimeZone.UTC)
        InstantType.deserialize("2015-01-01T12:10:30.001234+00:01") shouldBe
                LocalDateTime(2015, 1, 1, 12, 9, 30, 1_000_000).toInstant(TimeZone.UTC)
    }

    @Test
    fun dateTimeSerialize() {
        DateTimeType.serialize(LocalDateTime(2015, 1, 1, 12, 10, 30)) shouldBe "2015-01-01T12:10:30Z"
        DateTimeType.serialize(LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)) shouldBe "2015-01-01T12:10:30.001Z"
    }

    @Test
    fun dateTimeDeserialize() {
        DateTimeType.deserialize(0) shouldBe LocalDateTime(1970, 1, 1, 0, 0, 0)
        DateTimeType.deserialize(1618249875_000L) shouldBe LocalDateTime(2021, 4, 12, 17, 51, 15)
        DateTimeType.deserialize(2015) shouldBe LocalDateTime(2015, 1, 1, 0, 0, 0)
        DateTimeType.deserialize("2015") shouldBe LocalDateTime(2015, 1, 1, 0, 0, 0)
        DateTimeType.deserialize("2015-01") shouldBe LocalDateTime(2015, 1, 1, 0, 0, 0)
        DateTimeType.deserialize("2015-01-01") shouldBe LocalDateTime(2015, 1, 1, 0, 0, 0)
        DateTimeType.deserialize("2015-01-01T") shouldBe LocalDateTime(2015, 1, 1, 0, 0, 0)
        DateTimeType.deserialize("2015-01-01T12") shouldBe LocalDateTime(2015, 1, 1, 12, 0, 0)
        DateTimeType.deserialize("2015-01-01T12:10") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 0)
        DateTimeType.deserialize("2015-01-01T12:10:30") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30)
        DateTimeType.deserialize("2015-01-01T12:10:30Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30)
        DateTimeType.deserialize("2015-01-01T12:10:30-02") shouldBe LocalDateTime(2015, 1, 1, 14, 10, 30)
        DateTimeType.deserialize("2015-01-01T12:10:30+0230") shouldBe LocalDateTime(2015, 1, 1, 9, 40, 30)
        DateTimeType.deserialize("2015-01-01T12:10:30+02:00") shouldBe LocalDateTime(2015, 1, 1, 10, 10, 30)
        DateTimeType.deserialize("2015-01-01T12:10:30.1") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 100_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.1Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 100_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001234Z") shouldBe
                LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001234+00:01") shouldBe
                LocalDateTime(2015, 1, 1, 12, 9, 30, 1_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001234567Z") shouldBe
                LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
        shouldThrow<ValueDeserializationException> {
            DateTimeType.deserialize("2015-13")
        }
        shouldThrow<ValueDeserializationException> {
            DateTimeType.deserialize("2015-01-01T12:10:301")
        }
        shouldThrow<ValueDeserializationException> {
            DateTimeType.deserialize("2015-01-01T12:10:30.0012345678Z")
        }
    }

    @Test
    fun dateSerialize() {
        DateType.serialize(LocalDate(2015, 1, 1)) shouldBe "2015-01-01"
    }

    @Test
    fun dateDeserialize() {
        DateType.deserialize(0) shouldBe LocalDate(1970, 1, 1)
        DateType.deserialize(1618249875_000L) shouldBe LocalDate(2021, 4, 12)
        DateType.deserialize(2015) shouldBe LocalDate(2015, 1, 1)
        DateType.deserialize("2015") shouldBe LocalDate(2015, 1, 1)
    }
}