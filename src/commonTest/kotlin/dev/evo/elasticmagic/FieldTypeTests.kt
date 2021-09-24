package dev.evo.elasticmagic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime

import kotlin.test.Test

class FieldTypeTests {
    @Test
    fun dateSerialize() {
        DateTimeType.serialize(LocalDateTime(2015, 1, 1, 12, 10, 30)) shouldBe "2015-01-01T12:10:30Z"
        DateTimeType.serialize(LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)) shouldBe "2015-01-01T12:10:30.001Z"
    }

    @Test
    fun dateDeserialize() {
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
        DateTimeType.deserialize("2015-01-01T12:10:30.1") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 100_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.1Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 100_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001234Z") shouldBe
                LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
        DateTimeType.deserialize("2015-01-01T12:10:30.001234567Z") shouldBe
                LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
        shouldThrow<ValueDeserializationException> {
            DateTimeType.deserialize("2015-01-01T12:10:301")
        }
        shouldThrow<ValueDeserializationException> {
            DateTimeType.deserialize("2015-01-01T12:10:30.0012345678Z")
        }
    }
}
