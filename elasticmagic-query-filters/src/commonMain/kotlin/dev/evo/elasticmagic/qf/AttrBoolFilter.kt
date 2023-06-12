package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.ScriptedMetricAgg
import dev.evo.elasticmagic.aggs.ScriptedMetricAggResult
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.types.BooleanType
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.SimpleListType

fun encodeBoolAttrWithValue(attrId: Int, value: Boolean): Long {
    return (attrId.toLong() shl 1) or (if (value) 1L else 0L)
}

fun decodeBoolAttrAndValue(attrValue: Long): Pair<Int, Boolean> {
    return (attrValue ushr 1).toInt() to (attrValue and 1L == 1L)
}

private fun getAttrBoolSelectedValue(
    params: QueryFilterParams,
    paramName: String
) = params.asSequence()
    .mapNotNull { (keys, values) ->
        @Suppress("MagicNumber")
        when {
            keys.isEmpty() -> null
            keys[0] != paramName -> null
            keys.size == 2 -> {
                val attrId = IntType.deserializeTermOrNull(keys[1])
                if (attrId != null) {
                    val parsedValues = values.mapNotNull(BooleanType::deserializeTermOrNull)
                    if (parsedValues.isNotEmpty()) {
                        attrId to AttrBoolFacetFilter.SelectedValues(attrId, parsedValues)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            else -> null
        }
    }
    .toMap()

/**
 * Facet fiter for attribute values. An attribute value is a pair of 2
 * 32-bit values attribute id and value id combined as a single 64-bit field.
 */
class AttrBoolFacetFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<AttrBoolFacetFilterResult>(name) {

    data class SelectedValues(val attrId: Int, val values: List<Boolean>) {
        fun filterExpression(field: FieldOperations<Long>): QueryExpression {
            return if (values.size == 1) {
                field eq encodeBoolAttrWithValue(attrId, values[0])
            } else {
                field oneOf values.map { v -> encodeBoolAttrWithValue(attrId, v) }
            }
        }
    }

    /**
     * Parses [params] and prepares the [AttrFacetFilter] for applying.
     *
     * @param name - name of the filter
     * @param params - parameters that should be applied to a search query.
     *   Examples:
     *   - `mapOf(listOf("attrs", "1") to listOf("true"))`
     *   - `mapOf(listOf("attrs", "2") to listOf("false"))`
     */
    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedAttrBoolFacetFilter {
        val selectedValues = getAttrBoolSelectedValue(params, paramName)
        val facetFilters = selectedValues.values.map { w ->
            w.filterExpression(field)
        }
        val facetFilterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrBoolFacetFilter(this, name, paramName, facetFilterExpr, selectedValues)
    }
}

class PreparedAttrBoolFacetFilter(
    val filter: AttrBoolFacetFilter,
    name: String,
    paramName: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: Map<Int, AttrBoolFacetFilter.SelectedValues>,
) : PreparedFilter<AttrBoolFacetFilterResult>(name, paramName, facetFilterExpr) {
    private val otherFilterAggName = "qf:$name.filter"
    private val fullAggName = "qf:$name.full"
    private val filterFullAggName = "qf:$name.full.filter"
    private fun attrAggName(attrId: Int) = "qf:$name.$attrId"
    private fun filterAttrAggName(attrId: Int) = "qf:$name.$attrId.filter"

    companion object {
        const val DEFAULT_FULL_AGG_SIZE = 100
        internal val SELECTED_ATTR_INIT_SCRIPT = """
            state.buckets = new int[2];
        """.trimIndent()
        internal val SELECTED_ATTR_MAP_SCRIPT = """
            if (doc[params.attrsField].size() == 0) {
                return;
            }
            for (v in doc[params.attrsField]) {
                def attrId = (int) (v >>> 1);
                if (attrId != params.attrId) {
                    continue;
                }

                def value = (int) (v & 1);
                state.buckets[value]++;
            }
        """.trimIndent()
        internal val SELECTED_ATTR_COMBINE_SCRIPT = """
            return state.buckets;
        """.trimIndent()
        internal val SELECTED_ATTR_REDUCE_SCRIPT = """
            def buckets = new int[2];
            for (state in states) {
                buckets[0] += state[0];
                buckets[1] += state[1];
            }
            return buckets;
        """.trimIndent()
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
                    if (a != attrId) {
                        w.filterExpression(filter.field)
                    } else {
                        null
                    }
                }
            val attrAgg = ScriptedMetricAgg(
                SimpleListType(IntType),
                initScript = Script.Source(SELECTED_ATTR_INIT_SCRIPT),
                mapScript = Script.Source(SELECTED_ATTR_MAP_SCRIPT),
                combineScript = Script.Source(SELECTED_ATTR_COMBINE_SCRIPT),
                reduceScript = Script.Source(SELECTED_ATTR_REDUCE_SCRIPT),
                params = mapOf(
                    "attrId" to attrId,
                    "attrsField" to filter.field,
                )
            )
            if (otherAttrFacetFilterExpressions.isNotEmpty()) {
                attrAggs[filterAttrAggName(attrId)] = FilterAgg(
                    maybeWrapBool(Bool::filter, otherAttrFacetFilterExpressions),
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
                otherFilterAggName to FilterAgg(
                    maybeWrapBool(Bool::filter, otherFacetFilterExpressions),
                    aggs = attrAggs
                )
            )
        } else {
            attrAggs
        }

        searchQuery.aggs(aggs)

        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrBoolFacetFilterResult {
        val facets = mutableMapOf<Int, MutableList<AttrBoolFacetValue>>()

        val aggsResult = searchQueryResult.unwrapFilterAgg(otherFilterAggName)

        val fullAgg = aggsResult.facetAgg<TermsAggResult<Long>>(fullAggName)
        for (bucket in fullAgg.buckets) {
            val (attrId, value) = decodeBoolAttrAndValue(bucket.key)
            facets.getOrPut(attrId, ::mutableListOf)
                .add(AttrBoolFacetValue(value, bucket.docCount))
        }

        for ((attrId, selectedAttrValues) in selectedValues) {
            val attrAgg = aggsResult.facetAgg<ScriptedMetricAggResult<*>>(attrAggName(attrId))
            val counts = attrAgg.value as List<*>
            val facetValues = buildList {
                val falseCount = counts[0] as Int
                if (falseCount > 0 || false in selectedAttrValues.values) {
                    add(AttrBoolFacetValue(false, falseCount.toLong()))
                }
                val trueCount = counts[1] as Int
                if (trueCount > 0 || true in selectedAttrValues.values) {
                    add(AttrBoolFacetValue(true, trueCount.toLong()))
                }
            }.sortedByDescending { fv ->
                // Sort by count descending and then by value ascending
                fv.count shl 1 or (if (fv.value) 0 else 1)
            }
            facets[attrId] = facetValues.toMutableList()
        }

        return AttrBoolFacetFilterResult(
            name,
            paramName,
            facets = facets.mapValues { (attrId, values) ->
                AttrBoolFacet(attrId, values)
            }
        )
    }
}

class AttrBoolSimpleFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<BaseFilterResult>(name) {

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedAttrBoolExpressionFilter {
        val facetFilters = getAttrBoolSelectedValue(params, paramName)
            .values
            .map {
                it.filterExpression(field)
            }

        val filterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrBoolExpressionFilter(this, name, paramName, filterExpr)
    }
}

class PreparedAttrBoolExpressionFilter(
    val filter: AttrBoolSimpleFilter,
    name: String,
    paramName: String,
    private val filterExpression: QueryExpression?,
) : PreparedFilter<BaseFilterResult>(name, paramName, null) {

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        if (filterExpression != null) {
            searchQuery.filter(filterExpression)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): BaseFilterResult {
        return BaseFilterResult(
            name,
            paramName
        )
    }
}


data class AttrBoolFacetFilterResult(
    override val name: String,
    override val paramName: String,
    val facets: Map<Int, AttrBoolFacet>
) : FilterResult, Iterable<Map.Entry<Int, AttrBoolFacet>> by facets.entries

data class AttrBoolFacet(
    val attrId: Int,
    val values: List<AttrBoolFacetValue>
) : Iterable<AttrBoolFacetValue> by values

data class AttrBoolFacetValue(
    val value: Boolean,
    val count: Long,
)
