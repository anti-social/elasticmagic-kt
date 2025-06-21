package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.CompilerSet
import dev.evo.elasticmagic.compile.ElasticsearchFeatures
import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.StdDeserializer
import dev.evo.elasticmagic.serde.StdSerializer
import dev.evo.elasticmagic.types.FieldType
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf

@Suppress("UnnecessaryAbstractClass")
abstract class BaseTest {
    protected val serde = TestSerde()
    protected val serializer = serde.serializer
    protected val deserializer = serde.deserializer

    companion object {
        private val ALL_COMPILERS = listOf(
            CompilerSet(ElasticsearchFeatures.ES_6_0),
            CompilerSet(ElasticsearchFeatures.ES_7_0),
        )
    }

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

    data class CompileTestCtx(
        val compiler: CompilerSet,
        val serializer: Serializer,
    ) {
        fun Expression<Serializer.ObjectCtx>.compile(): Map<String, Any?> {
            val obj = serializer.obj {
                compiler.searchQuery.visit(this, this@compile)
            }
            return obj.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap()
        }

        infix fun Expression<Serializer.ObjectCtx>.shouldCompileInto(
            expected: Map<String, Any>,
        ) {
            compile() shouldContainExactly expected
        }

        fun FieldType<*, *>.compile(): Map<String, Any?> {
            val obj = serializer.obj {
                compiler.mapping.visit(this, this@compile)
            }
            return obj.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap()
        }

        infix fun FieldType<*, *>.shouldCompileInto(
            expected: Map<String, Any>,
        ) {
            compile() shouldContainExactly expected
        }
    }

    fun withCompilers(block: CompileTestCtx.() -> Unit) {
        for (compiler in ALL_COMPILERS) {
            CompileTestCtx(compiler, serializer).block()
        }
    }
}
