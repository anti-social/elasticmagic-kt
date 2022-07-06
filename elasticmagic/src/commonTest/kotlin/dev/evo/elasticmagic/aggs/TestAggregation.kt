package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.BaseExpressionTest

@Suppress("UnnecessaryAbstractClass")
abstract class TestAggregation : BaseExpressionTest() {
    protected fun <A: Aggregation<R>, R: AggregationResult> process(
        agg: A, rawResult: Map<String, Any?>
    ): R {
        return agg.processResult(deserializer.wrapObj(rawResult))
    }
}
