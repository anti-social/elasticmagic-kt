package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.date
import dev.evo.elasticmagic.serde.Serializer
import io.kotest.matchers.types.shouldBeInstanceOf

@Suppress("UnnecessaryAbstractClass")
abstract class BaseExpressionTest : BaseTest() {
    protected val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    protected fun Expression<Serializer.ObjectCtx>.compile(): Map<String, Any?> {
        val obj = serializer.obj {
            compiler.visit(this, this@compile)
        }
        return obj.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap()
    }

    protected object MovieDoc : Document() {
        val genre by keyword()
        val rating by float()
        val isColored by boolean("is_colored")
        val numRatings by int("num_ratings")
        val releaseDate by date("release_date")
        val name by text()
        val description by text()
    }
}
