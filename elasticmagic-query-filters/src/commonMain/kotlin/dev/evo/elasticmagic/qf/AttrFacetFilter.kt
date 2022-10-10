package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.FilterAggResult
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.doc.BoundRuntimeField
import dev.evo.elasticmagic.doc.RootFieldSet
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.LongType

fun encodeAttrWithValue(attrId: Int, valueId: Int): Long {
    return (attrId.toLong() shl 32) or valueId.toLong()
}

fun decodeAttrAndValue(attrValue: Long): Pair<Int, Int> {
    return (attrValue ushr 32).toInt() to attrValue.toInt()
}

class AttrFacetFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<PreparedAttrFacetFilter, AttrFacetFilterResult>(name) {
    data class SelectedValues(val attrId: Int, val valueIds: List<Int>, val mode: FacetFilterMode) {
        fun filterExpression(field: FieldOperations<Long>): QueryExpression {
            return if (valueIds.size == 1) {
                field eq encodeAttrWithValue(attrId, valueIds[0])
            } else if (mode == FacetFilterMode.UNION){
                field oneOf valueIds.map { v -> encodeAttrWithValue(attrId, v) }
            } else {
                Bool.filter(valueIds.map { v -> field eq encodeAttrWithValue(attrId, v) })
            }
        }
    }

    override fun prepare(name: String, params: QueryFilterParams): PreparedAttrFacetFilter {
        val selectedValues = params.asSequence()
            .mapNotNull { (keys, values) ->
                when {
                    keys.isEmpty() -> null
                    values.isEmpty() -> null
                    keys[0] != name -> null
                    keys.size == 2 || keys.size == 3 -> {
                        val mode = when {
                            keys.size == 2 -> FacetFilterMode.UNION
                            keys.size == 3 && keys[2] == "any" -> FacetFilterMode.UNION
                            keys.size == 3 && keys[2] == "all" -> FacetFilterMode.INTERSECT
                            else -> null
                        }
                        val attrId = IntType.deserializeTermOrNull(keys[1])
                        if (mode == null || attrId == null) {
                            null
                        } else {
                            attrId to SelectedValues(attrId, values.mapNotNull(IntType::deserializeTermOrNull), mode)
                        }
                    }
                    else -> null
                }
            }
            .toMap()
        println(selectedValues)

        val facetFilters = selectedValues.values.map { w ->
            w.filterExpression(field)
        }
        val facetFilterExpr = if (facetFilters.size == 1) {
            facetFilters[0]
        } else {
            Bool.filter(facetFilters)
        }

        return PreparedAttrFacetFilter(this, name, facetFilterExpr, selectedValues)
    }
}

class PreparedAttrFacetFilter(
    val filter: AttrFacetFilter,
    name: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: Map<Int, AttrFacetFilter.SelectedValues>,
) : PreparedFilter<AttrFacetFilterResult>(name, facetFilterExpr) {
    private val fullAggName = "qf:$name.full"
    private val filterFullAggName = "qf:$name.full.filter"
    private val filterAggName = "qf:$name.filter"
    private val attrAggNameRe = "qf:${Regex.escape(name)}\\.(\\d+)".toRegex()
    private fun attrAggName(attrId: Int) = "qf:$name.$attrId"
    private val filterAttrAggNameRe = "qf:${Regex.escape(name)}\\.filter\\.(\\d+)".toRegex()
    private fun filterAttrAggName(attrId: Int) = "qf:$name.filter.$attrId"

    companion object {
        const val DEFAULT_FULL_AGG_SIZE = 10_000
        const val DEFAULT_ATTR_AGG_SIZE = 100
    }

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val attrAggs = mutableMapOf<String, Aggregation<*>>()
        val fullAgg = TermsAgg(filter.field, size = DEFAULT_FULL_AGG_SIZE)
        if (facetFilterExpr != null) {
            attrAggs[filterFullAggName] = FilterAgg(facetFilterExpr, aggs = mapOf(fullAggName to fullAgg))
        } else {
            attrAggs[fullAggName] = fullAgg
        }

        for (attrId in selectedValues.keys) {
            val otherAttrFacetFilterExpressions = selectedValues
                .mapNotNull { (a, w) ->
                    if (w.mode == FacetFilterMode.INTERSECT || a != attrId) {
                        w.filterExpression(filter.field)
                    } else {
                        null
                    }
                }
            val attrField = BoundRuntimeField(
                "attr_${attrId}", LongType,
                Script.Source(
                    """
                        int attrsLen = doc[params.attrsField].size();
                        if (attrsLen > 0) {
                            for (def attrValue : doc[params.attrsField]) {
                                long attrId = attrValue >> 32;
                                if (attrId == params.attrId) {
                                    emit(attrValue & 0xffffffffL);
                                }
                            }
                        }
                        """.trimIndent(),
                    params = mapOf(
                        "attrId" to attrId,
                        "attrsField" to filter.field,
                    )
                ),
                RootFieldSet
            )
            searchQuery.runtimeMappings(
                "attr_${attrId}" to attrField
            )
            val attrAgg = TermsAgg(attrField, size = DEFAULT_ATTR_AGG_SIZE)
            if (otherAttrFacetFilterExpressions.isNotEmpty()) {
                attrAggs[filterAttrAggName(attrId)] = FilterAgg(
                    Bool.filter(otherAttrFacetFilterExpressions),
                    aggs = mapOf(
                        attrAggName(attrId) to attrAgg
                    )
                )
            } else {
                attrAggs[attrAggName(attrId)] = attrAgg
            }
        }

        val aggs = if (otherFacetFilterExpressions.isNotEmpty()) {
            mutableMapOf(
                filterAggName to FilterAgg(
                    Bool.filter(otherFacetFilterExpressions),
                    aggs = attrAggs
                )
            )
        } else {
            attrAggs
        }

        println(aggs)
        searchQuery.aggs(aggs)

        println(facetFilterExpr)
        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrFacetFilterResult {
        var aggResult = searchQueryResult as AggAwareResult
        if (aggResult.aggs.containsKey(filterAggName)) {
            aggResult = aggResult.agg<SingleBucketAggResult>(filterAggName)
        }

        val facetValues = mutableMapOf<Int, MutableList<AttrFacetValue>>()

        val fullAgg = (aggResult.aggIfExists<FilterAggResult>(filterFullAggName) ?: aggResult)
            .agg<TermsAggResult<Long>>(fullAggName)
        for (bucket in fullAgg.buckets) {
            val (attrId, valueId) = decodeAttrAndValue(bucket.key)
            if (attrId in selectedValues) {
                continue
            }
            println("$attrId: $valueId")
            facetValues.getOrPut(attrId, ::mutableListOf)
                .add(AttrFacetValue(valueId, bucket.docCount))
        }

        for ((aggName, agg) in aggResult.aggs) {
            println(aggName)
            val filteredAttrIdMatch = filterAttrAggNameRe.matchEntire(aggName)
            val (attrId, attrAgg) = if (filteredAttrIdMatch != null) {
                val attrId = filteredAttrIdMatch.groups[1]?.value?.toInt() ?: continue
                val attrAgg = (agg as FilterAggResult).agg<TermsAggResult<Long>>(attrAggName(attrId))
                attrId to attrAgg
            } else {
                val attrIdMatch = attrAggNameRe.matchEntire(aggName) ?: continue
                val attrId = attrIdMatch.groups[1]?.value?.toInt() ?: continue
                attrId to (agg as TermsAggResult<*>)
            }
            println("$attrId: size - ${attrAgg.buckets.size}")
            facetValues[attrId] = attrAgg.buckets
                .map { bucket -> AttrFacetValue((bucket.key as Long).toInt(), bucket.docCount) }
                .toMutableList()
        }
        println(facetValues.size)

        return AttrFacetFilterResult(
            name,
            facets = facetValues.mapValues { (attrId, values) ->
                AttrFacet(attrId, values)
            }
        )
    }
}

data class AttrFacetFilterResult(
    override val name: String,
    val facets: Map<Int, AttrFacet>
) : FilterResult, Iterable<Map.Entry<Int, AttrFacet>> by facets.entries

data class AttrFacet(
    val attrId: Int,
    val values: List<AttrFacetValue>
) : Iterable<AttrFacetValue> by values

data class AttrFacetValue(
    val value: Int,
    val count: Long,
)
