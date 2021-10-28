package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Deserializer
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

        class ArrayCtx : StdSerializer.ArrayCtx(ArrayList())
    }
    protected val serializer = TestSerializer

    object TestDeserializer : StdDeserializer() {
        override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
            TODO("not implemented")
        }

        fun wrapObj(obj: Map<String, Any?>): ObjectCtx {
            return ObjectCtx(obj)
        }
    }
    protected val deserializer = TestDeserializer
}
