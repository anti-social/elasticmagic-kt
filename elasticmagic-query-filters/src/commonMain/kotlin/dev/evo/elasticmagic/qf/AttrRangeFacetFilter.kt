package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.types.IntType

class AttrRangeFacetFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<PreparedAttrRangeFacetFilter, AttrRangeFacetFilterResult>(name) {

    override fun prepare(name: String, params: QueryFilterParams): PreparedAttrRangeFacetFilter {
        val selectedValues = buildMap<_, Pair<Float?, Float?>> {
            for ((keys, values) in params) {
                when {
                    keys.isEmpty() -> {}
                    values.isEmpty() -> {}
                    keys[0] != name -> {}
                    keys.size == 3 -> {
                        val attrId = IntType.deserializeTermOrNull(keys[1]) ?: continue
                        val value = FloatType.deserializeTermOrNull(values.last())
                        when (keys[2]){
                            "gte" -> {
                                val rng = getOrPut(attrId) { Pair(null, null)}
                                put(attrId, rng.copy(first = value))
                            }
                            "lte" -> {
                                val rng = getOrPut(attrId) { Pair(null, null)}
                                put(attrId, rng.copy(first = value))
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
        }
        println(selectedValues)

        return PreparedAttrRangeFacetFilter(this, name, null, selectedValues)
    }
}

class PreparedAttrRangeFacetFilter(
    val filter: AttrRangeFacetFilter,
    name: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: Map<Int, Pair<Float?, Float?>>
) : PreparedFilter<AttrRangeFacetFilterResult>(name, facetFilterExpr) {
    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        TODO("not implemented")
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrRangeFacetFilterResult {
        TODO("not implemented")
    }
}

data class AttrRangeFacetFilterResult(
    override val name: String,
) : FilterResult