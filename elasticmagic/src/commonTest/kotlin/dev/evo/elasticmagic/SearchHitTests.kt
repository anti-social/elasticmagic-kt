package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.AnyField
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.RootFieldSet
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.ValueDeserializationException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly

import kotlin.test.Test

class AnyIntField(name: String) : BoundField<Int, Int>(
    name,
    IntType,
    Params(),
    RootFieldSet
)

class SearchHitTests {
    @Test
    fun fields() {
        SearchHit.Fields(mapOf()).let { fields ->
            ("id" !in fields).shouldBeTrue()
            shouldThrow<NoSuchElementException> {
                fields["id"]
            }
        }

        SearchHit.Fields(mapOf("tags" to listOf("access point", "wifi"))).let { fields ->
            (AnyIntField("tags") in fields).shouldBeTrue()
            shouldThrow<ValueDeserializationException> {
                fields[AnyIntField("tags")]
            }
        }

        SearchHit.Fields(mapOf("tags" to listOf(3, 2, 1))).let { fields ->
            fields["tags"] shouldContainExactly listOf(3, 2, 1)
            fields[AnyField("tags")] shouldContainExactly listOf(3, 2, 1)
        }
    }
}
