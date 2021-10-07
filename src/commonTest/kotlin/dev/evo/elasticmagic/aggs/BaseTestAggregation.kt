package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Expression
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.StdDeserializer
import dev.evo.elasticmagic.serde.StdSerializer

abstract class BaseTestAggregation {
    protected val serializer = object : StdSerializer() {
        override fun objToString(obj: Map<String, Any?>): String {
            TODO("not implemented")
        }
    }
    protected val deserializer = object : StdDeserializer() {
        override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
            TODO("not implemented")
        }

    }
    protected val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    protected fun Expression.compile(): Map<String, *> {
        return serializer.buildObj {
            compiler.visit(this, this@compile)
        }
    }

    protected fun <A: Aggregation<R>, R: AggregationResult> process(
        agg: A, rawResult: Map<String, Any?>
    ): R {
        return agg.processResult(deserializer.wrapObj(rawResult))
    }

    protected object MovieDoc : Document() {
        val genre by keyword()
        val rating by float()
        val numRatings by int("num_ratings")
        val releaseDate by date("release_date")
    }
}
