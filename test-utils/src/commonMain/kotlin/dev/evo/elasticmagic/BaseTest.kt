package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.StdDeserializer
import dev.evo.elasticmagic.serde.StdSerializer

@Suppress("UnnecessaryAbstractClass")
abstract class BaseTest {
    object TestSerializer : StdSerializer(::ObjectCtx, ::ArrayCtx) {
        class ObjectCtx : StdSerializer.ObjectCtx(HashMap()) {
            override fun serialize(): String {
                TODO("not implemented")
            }

            fun toMap(): Map<String, Any?> {
                return map.toMap()
            }
        }

        class ArrayCtx : StdSerializer.ArrayCtx(ArrayList()) {
            override fun serialize(): String {
                TODO("not implemented")
            }

            fun toList(): List<Any?> {
                return array.toList()
            }
        }
    }

    object TestDeserializer : StdDeserializer() {
        override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
            TODO("not implemented")
        }

        fun wrapObj(obj: Map<String, Any?>): ObjectCtx {
            return ObjectCtx(obj)
        }
    }

    class TestSerde : Serde {
        override val contentType: String = "test"

        override val serializer: TestSerializer = TestSerializer
        override val deserializer: TestDeserializer = TestDeserializer

    }

    protected val serde = TestSerde()
    protected val serializer = serde.serializer
    protected val deserializer = serde.deserializer
}
