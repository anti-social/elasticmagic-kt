package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.date

import io.kotest.matchers.types.shouldBeInstanceOf

@Suppress("UnnecessaryAbstractClass")
abstract class TestAggregation : BaseTest() {
    protected val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    protected fun Expression.compile(): Map<String, Any?> {
        val obj = serializer.obj {
            compiler.visit(this, this@compile)
        }
        return obj.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap()
    }

    protected fun <A: Aggregation<R>, R: AggregationResult> process(
        agg: A, rawResult: Map<String, Any?>
    ): R {
        return agg.processResult(deserializer.wrapObj(rawResult))
    }

    protected object MovieDoc : Document() {
        val genre by keyword()
        val rating by float()
        val isColored by boolean("is_colored")
        val numRatings by int("num_ratings")
        val releaseDate by date("release_date")
    }
}
