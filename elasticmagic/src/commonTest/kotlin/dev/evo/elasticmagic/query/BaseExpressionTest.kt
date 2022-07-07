package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.compile.ElasticsearchFeatures
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.doc.date
import dev.evo.elasticmagic.serde.Serializer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

@Suppress("UnnecessaryAbstractClass")
abstract class BaseExpressionTest : BaseTest() {
    protected val compiler = SearchQueryCompiler(
        ElasticsearchFeatures.ES_6_0
    )

    protected fun Expression<Serializer.ObjectCtx>.compile(): Map<String, Any?> {
        val obj = serializer.obj {
            compiler.visit(this, this@compile)
        }
        return obj.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap()
    }

    protected fun Expression<Serializer.ArrayCtx>.compile(): List<Any?> {
        val arr = serializer.array {
            compiler.visit(this, this@compile)
        }
        return arr.shouldBeInstanceOf<TestSerializer.ArrayCtx>().toList()
    }

    protected fun checkClone(expression: Expression<*>) {
        val clone = expression.clone()
        clone.shouldNotBeSameInstanceAs(expression)
        clone shouldBe expression
    }

    protected class StarDoc(field: BoundField<BaseDocSource, Nothing>) : SubDocument(field) {
        val name by text()
        val rank by float()
    }

    protected object MovieDoc : Document() {
        val status by byte()
        val genre by keyword()
        val rating by float()
        val stars by nested(::StarDoc)
        val isColored by boolean("is_colored")
        val numRatings by int("num_ratings")
        val releaseDate by date("release_date")
        val name by text()
        val description by text()
    }
}
