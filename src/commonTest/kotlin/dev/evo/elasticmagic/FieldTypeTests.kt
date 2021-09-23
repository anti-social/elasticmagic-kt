package dev.evo.elasticmagic

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime

import kotlin.test.Test

class FieldTypeTests {
    @Test
    fun dateSerialize() {
        DateType.serialize(LocalDateTime(2015, 1, 1, 12, 10, 30)) shouldBe "2015-01-01T12:10:30Z"
        DateType.serialize(LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)) shouldBe "2015-01-01T12:10:30.001Z"
    }

    @Test
    fun dateDeserialize() {
        DateType.deserialize(0) shouldBe LocalDateTime(1970, 1, 1, 0, 0, 0)
        DateType.deserialize(1618249875_000) shouldBe LocalDateTime(2021, 4, 12, 17, 51, 15)
        DateType.deserialize("2015-01-01T12:10:30Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30)
        DateType.deserialize("2015-01-01T12:10:30.001Z") shouldBe LocalDateTime(2015, 1, 1, 12, 10, 30, 1_000_000)
    }
}
