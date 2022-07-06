package dev.evo.elasticmagic.types

import dev.evo.elasticmagic.serde.Platform
import dev.evo.elasticmagic.serde.platform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.test.Test

class FieldTypeTests {
    @Test
    fun byte() {
        ByteType.name shouldBe "byte"
        ByteType.termType shouldBe Byte::class

        ByteType.serialize(0).shouldBeInstanceOf<Int>()
        ByteType.serialize(127) shouldBe 127
        ByteType.serialize(-128) shouldBe -128

        ByteType.deserialize(127) shouldBe 127
        ByteType.deserialize(127L) shouldBe 127
        ByteType.deserialize(-128) shouldBe -128
        ByteType.deserialize(-128L) shouldBe -128
        shouldThrow<ValueDeserializationException> {
            ByteType.deserialize(128)
        }
        shouldThrow<ValueDeserializationException> {
            ByteType.deserialize(-129)
        }
        ByteType.deserialize("1") shouldBe 1
        shouldThrow<ValueDeserializationException> {
            ByteType.deserialize("zero")
        }

        ByteType.serializeTerm(0).shouldBeInstanceOf<Int>()
        ByteType.serializeTerm(127) shouldBe 127
        ByteType.serializeTerm(-128) shouldBe -128

        ByteType.deserializeTerm(127) shouldBe 127
        ByteType.deserializeTerm(127L) shouldBe 127
        ByteType.deserializeTerm(-128) shouldBe -128
        ByteType.deserializeTerm(-128L) shouldBe -128
        shouldThrow<ValueDeserializationException> {
            ByteType.deserialize(128)
        }
        shouldThrow<ValueDeserializationException> {
            ByteType.deserializeTerm(-129)
        }
        ByteType.deserializeTerm("1") shouldBe 1
        shouldThrow<ValueDeserializationException> {
            ByteType.deserialize("one")
        }
    }

    @Test
    fun short() {
        ShortType.name shouldBe "short"
        ShortType.termType shouldBe Short::class

        ShortType.serialize(0).shouldBeInstanceOf<Int>()
        ShortType.serialize(32767) shouldBe 32767
        ShortType.serialize(-32768) shouldBe -32768

        ShortType.deserialize(32767) shouldBe 32767
        ShortType.deserialize(32767L) shouldBe 32767
        ShortType.deserialize(-32768) shouldBe -32768
        ShortType.deserialize(-32768) shouldBe -32768
        shouldThrow<ValueDeserializationException> {
            ShortType.deserialize(32768)
        }
        shouldThrow<ValueDeserializationException> {
            ShortType.deserialize(-32769)
        }
        ShortType.deserialize("1") shouldBe 1
        shouldThrow<ValueDeserializationException> {
            ShortType.deserialize("zero")
        }

        ShortType.serializeTerm(0).shouldBeInstanceOf<Int>()
        ShortType.serializeTerm(32767) shouldBe 32767
        ShortType.serializeTerm(-32768) shouldBe -32768

        ShortType.deserializeTerm(32767) shouldBe 32767
        ShortType.deserializeTerm(32767L) shouldBe 32767
        ShortType.deserializeTerm(-32768) shouldBe -32768
        ShortType.deserializeTerm(-32768) shouldBe -32768
        shouldThrow<ValueDeserializationException> {
            ShortType.deserialize(32768)
        }
        shouldThrow<ValueDeserializationException> {
            ShortType.deserializeTerm(-32969)
        }
        ShortType.deserializeTerm("1") shouldBe 1
        shouldThrow<ValueDeserializationException> {
            ShortType.deserializeTerm("one")
        }
    }

    @Test
    fun boolean() {
        BooleanType.serialize(false) shouldBe false
        BooleanType.serialize(true) shouldBe true

        BooleanType.deserialize(false) shouldBe false
        BooleanType.deserialize("true") shouldBe true
        BooleanType.deserialize("false") shouldBe false
        shouldThrow<ValueDeserializationException> {
            BooleanType.deserialize("t")
        }
        shouldThrow<ValueDeserializationException> {
            BooleanType.deserialize(1)
        }

        BooleanType.serializeTerm(false) shouldBe false
        BooleanType.serializeTerm(true) shouldBe true

        BooleanType.deserializeTerm(true) shouldBe true
        BooleanType.deserializeTerm("true") shouldBe true
        BooleanType.deserializeTerm("false") shouldBe false
        BooleanType.deserializeTerm(0) shouldBe false
        BooleanType.deserializeTerm(1) shouldBe true
        BooleanType.deserializeTerm(0.0) shouldBe false
        BooleanType.deserializeTerm(0.01) shouldBe true
    }

    @Test
    fun intRange() {
        IntRangeType.serialize(Range(gte = 5, lte = 10))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gte" to 5,
                "lte" to 10,
            )
        IntRangeType.serialize(Range(gt = -30, lt = 0))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gt" to -30,
                "lt" to 0,
        )

        IntRangeType.deserialize(mapOf("gt" to 1)) shouldBe Range(gt = 1)
        IntRangeType.deserialize(mapOf("lte" to "-1")) shouldBe Range(lte = -1)
        shouldThrow<ValueDeserializationException> {
            IntRangeType.deserialize(mapOf("gt" to Int.MAX_VALUE.toLong() + 1))
        }
        shouldThrow<ValueDeserializationException> {
            IntRangeType.deserialize(mapOf("lt" to Int.MIN_VALUE.toLong() - 1))
        }
        shouldThrow<ValueDeserializationException> {
            IntRangeType.deserialize(mapOf("gte" to "one"))
        }

        IntRangeType.serializeTerm(0) shouldBe 0
        IntRangeType.serializeTerm(0) shouldBe 0

        IntRangeType.deserializeTerm(-1) shouldBe -1
        if (platform == Platform.JS) {
            IntRangeType.deserializeTerm(-1.0) shouldBe -1
        } else {
            shouldThrow<ValueDeserializationException> {
                IntRangeType.deserializeTerm(-1.0)
            }
        }
    }

    @Test
    fun longRange() {
        LongRangeType.serialize(Range(gte = 5, lte = 10))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gte" to 5L,
                "lte" to 10L,
            )
        LongRangeType.serialize(Range(gt = -30, lt = 0))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gt" to -30L,
                "lt" to 0L,
            )

        LongRangeType.deserialize(mapOf("gt" to 1)) shouldBe Range(gt = 1L)
        LongRangeType.deserialize(mapOf("lt" to Long.MIN_VALUE)) shouldBe Range(lt = Long.MIN_VALUE)
        LongRangeType.deserialize(mapOf("lte" to "-1")) shouldBe Range(lte = -1L)
        if (platform == Platform.JS) {
            LongRangeType.deserialize(mapOf("gte" to 1.0)) shouldBe Range(gte = 1L)
        } else {
            shouldThrow<ValueDeserializationException> {
                LongRangeType.deserialize(mapOf("gte" to 1.0))
            }
        }
        shouldThrow<ValueDeserializationException> {
            LongRangeType.deserialize(mapOf("lte" to "max"))
        }

        LongRangeType.serializeTerm(0) shouldBe 0L
        LongRangeType.serializeTerm(1L) shouldBe 1L

        LongRangeType.deserializeTerm(-1) shouldBe -1
        LongRangeType.deserializeTerm("0") shouldBe 0L
        if (platform == Platform.JS) {
            LongRangeType.deserializeTerm(0.0) shouldBe 0
        } else {
            shouldThrow<ValueDeserializationException> {
                LongRangeType.deserializeTerm(0.0)
            }
        }
        shouldThrow<ValueDeserializationException> {
            LongRangeType.deserializeTerm("0.0")
        }
    }

    @Test
    fun floatRange() {
        FloatRangeType.serialize(Range(gte = 5.0F, lte = 10.0F))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gte" to 5.0F,
                "lte" to 10.0F,
            )
        FloatRangeType.serialize(Range(gt = -30F, lt = 0F))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gt" to -30F,
                "lt" to 0F,
            )

        FloatRangeType.deserialize(mapOf("gt" to 1)) shouldBe Range(gt = 1F)
        FloatRangeType.deserialize(mapOf("lt" to Int.MAX_VALUE.toFloat())) shouldBe
                Range(lt = Int.MAX_VALUE.toFloat())
        if (platform == Platform.JS) {
            FloatRangeType.deserialize(mapOf("lt" to (Int.MAX_VALUE - 1).toFloat())) shouldBe
                    Range(lt = (Int.MAX_VALUE - 1).toFloat())

        } else {
            FloatRangeType.deserialize(mapOf("lt" to (Int.MAX_VALUE - 1).toFloat())) shouldBe
                    Range(lt = Int.MAX_VALUE.toFloat())
        }
        FloatRangeType.deserialize(mapOf("lte" to "-1")) shouldBe Range(lte = -1F)
        FloatRangeType.deserialize(mapOf("gte" to "-1.1")) shouldBe Range(gte = -1.1F)
        shouldThrow<ValueDeserializationException> {
            FloatRangeType.deserialize(mapOf("gte" to "+Inf"))
        }

        FloatRangeType.serializeTerm(0F) shouldBe 0.0F

        FloatRangeType.deserializeTerm(-1) shouldBe -1F
        FloatRangeType.deserializeTerm("0.0") shouldBe 0F
        FloatRangeType.deserializeTerm("NaN").isNaN().shouldBeTrue()
        shouldThrow<ValueDeserializationException> {
            FloatRangeType.deserializeTerm("-Inf")
        }
    }

    @Test
    fun doubleRange() {
        DoubleRangeType.serialize(Range(gt = 5.0, gte = 5.0, lt = 20.0, lte = 10.0))
            .shouldBeInstanceOf<Map<String, Any>>() shouldContainExactly mapOf(
                "gt" to 5.0,
                "gte" to 5.0,
                "lt" to 20.0,
                "lte" to 10.0,
            )

        DoubleRangeType.deserialize(mapOf("gt" to 1)) shouldBe Range(gt = 1.0)
        DoubleRangeType.deserialize(mapOf("lt" to Int.MAX_VALUE.toDouble())) shouldBe
                Range(lt = Int.MAX_VALUE.toDouble())
        DoubleRangeType.deserialize(mapOf("lt" to Int.MAX_VALUE.toDouble())) shouldNotBe
                Range(lt = (Int.MAX_VALUE - 1).toDouble())
        DoubleRangeType.deserialize(mapOf("lt" to Long.MAX_VALUE.toDouble())) shouldBe
                Range(lt = Long.MAX_VALUE.toDouble())
        DoubleRangeType.deserialize(mapOf("lt" to Long.MAX_VALUE.toDouble())) shouldBe
                Range(lt = (Long.MAX_VALUE - 1).toDouble())
        DoubleRangeType.deserialize(mapOf("lte" to "-1")) shouldBe Range(lte = -1.0)
        DoubleRangeType.deserialize(mapOf("gte" to "-1.1")) shouldBe Range(gte = -1.1)
        shouldThrow<ValueDeserializationException> {
            DoubleRangeType.deserialize(mapOf("gte" to "+Inf"))
        }

        DoubleRangeType.serializeTerm(0.1) shouldBe 0.1

        DoubleRangeType.deserializeTerm(-1) shouldBe -1.0
        DoubleRangeType.deserializeTerm("0.0") shouldBe 0.0
        DoubleRangeType.deserializeTerm(Long.MIN_VALUE) shouldBe Long.MIN_VALUE.toDouble()
        shouldThrow<ValueDeserializationException> {
            DoubleRangeType.deserializeTerm("-Inf")
        }
    }

    enum class CardStatus(val id: Int) {
        ISSUED(1), ACTIVATED(0), TEMP_BLOCKED(3), BLOCKED(2)
    }

    @Test
    fun enum() {
        val cardStatusType = EnumFieldType(
            enumValues(), CardStatus::id, IntType, CardStatus::class
        )
        cardStatusType.name shouldBe "integer"

        cardStatusType.serialize(CardStatus.ISSUED) shouldBe 1
        cardStatusType.deserialize(0) shouldBe CardStatus.ACTIVATED
        cardStatusType.deserialize("2") shouldBe CardStatus.BLOCKED
        shouldThrow<ValueDeserializationException> {
            cardStatusType.deserialize("4")
        }
        shouldThrow<ValueDeserializationException> {
            cardStatusType.deserialize("ISSUED")
        }

        cardStatusType.serializeTerm(CardStatus.ISSUED) shouldBe 1
        cardStatusType.deserializeTerm(0) shouldBe CardStatus.ACTIVATED
        cardStatusType.deserializeTerm("3") shouldBe CardStatus.TEMP_BLOCKED
        shouldThrow<ValueDeserializationException> {
            cardStatusType.deserializeTerm("4")
        }
        shouldThrow<ValueDeserializationException> {
            cardStatusType.deserializeTerm("ISSUED")
        }
    }
}
