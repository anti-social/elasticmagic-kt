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
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.LongType
import dev.evo.elasticmagic.types.SimpleListType

fun encodeAttrWithValue(attrId: Int, valueId: Int): Long {
    return (attrId.toLong() shl Int.SIZE_BITS) or valueId.toLong()
}

fun decodeAttrAndValue(attrValue: Long): Pair<Int, Int> {
    return (attrValue ushr Int.SIZE_BITS).toInt() to attrValue.toInt()
}

/**
 * Facet fiter for attribute values. An attribute value is a pair of 2
 * 32-bit values attribute id and value id combined as a single 64-bit field.
 */
class AttrFacetFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<PreparedAttrFacetFilter, AttrFacetFilterResult>(name) {

    data class SelectedValues(val attrId: Int, val valueIds: List<Int>, val mode: FilterMode) {
        fun filterExpression(field: FieldOperations<Long>): QueryExpression {
            return if (valueIds.size == 1) {
                field eq encodeAttrWithValue(attrId, valueIds[0])
            } else if (mode == FilterMode.UNION) {
                field oneOf valueIds.map { v -> encodeAttrWithValue(attrId, v) }
            } else {
                Bool.filter(valueIds.map { v -> field eq encodeAttrWithValue(attrId, v) })
            }
        }
    }

    /**
     * Parses [params] and prepares the [AttrFacetFilter] for applying.
     *
     * @param name - name of the filter
     * @param params - parameters that should be applied to a search query.
     *   Examples:
     *   - `mapOf(listOf("attrs", "1") to listOf("12", "13"))`
     *   - `mapOf(listOf("attrs", "2", "all") to listOf("101", "102"))
     */
    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedAttrFacetFilter {
        val selectedValues = params.asSequence()
            .mapNotNull { (keys, values) ->
                @Suppress("MagicNumber")
                when {
                    keys.isEmpty() -> null
                    keys[0] != paramName -> null
                    keys.size == 2 || keys.size == 3 -> {
                        val mode = when {
                            keys.size == 2 -> FilterMode.UNION
                            keys.size == 3 && keys[2] == "any" -> FilterMode.UNION
                            keys.size == 3 && keys[2] == "all" -> FilterMode.INTERSECT
                            else -> null
                        }
                        val attrId = IntType.deserializeTermOrNull(keys[1])
                        val parsedValues = values.mapNotNull(IntType::deserializeTermOrNull)
                        if (mode == null || attrId == null || parsedValues.isEmpty()) {
                            null
                        } else {
                            attrId to SelectedValues(attrId, parsedValues, mode)
                        }
                    }
                    else -> null
                }
            }
            .toMap()
        val facetFilters = selectedValues.values.map { w ->
            w.filterExpression(field)
        }
        val facetFilterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrFacetFilter(this, name, paramName, facetFilterExpr, selectedValues)
    }
}

class PreparedAttrFacetFilter(
    val filter: AttrFacetFilter,
    name: String,
    paramName: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: Map<Int, AttrFacetFilter.SelectedValues>,
) : PreparedFilter<AttrFacetFilterResult>(name, paramName, facetFilterExpr) {
    private val otherFilterAggName = "qf:$name.filter"
    private val fullAggName = "qf:$name.full"
    private val filterFullAggName = "qf:$name.full.filter"
    private fun attrAggName(attrId: Int) = "qf:$name.$attrId"
    private fun filterAttrAggName(attrId: Int) = "qf:$name.$attrId.filter"

    companion object {
        const val DEFAULT_FULL_AGG_SIZE = 10_000
        const val DEFAULT_ATTR_AGG_SIZE = 100
        private const val INT_MASK = 0xffff_ffffL
        internal val SELECTED_ATTR_INIT_SCRIPT = """
            state.buckets = new HashMap();
        """.trimIndent()
        internal val SELECTED_ATTR_MAP_SCRIPT = """
            if (doc[params.attrsField].size() == 0) {
                return;
            }
            for (v in doc[params.attrsField]) {
                def attrId = (int) (v >>> 32);
                if (attrId != params.attrId) {
                    continue;
                }

                def value = (int) v;
                state.buckets.compute(
                    value,
                    (_, docCount) -> {
                        if (docCount == null) {
                            return 1;
                        } else {
                            return docCount + 1;
                        }
                    }
                );
            }
        """.trimIndent()
        // Entries are longs where higher 32 bits is a number of documents
        // and lower 32 bits is a value (bucket key). This trick allows
        // to sort entries by number of documents and select `params.size * 1.5 + 10` top entries.
        // The goal is to minimize heap allocations.
        private const val QUEUE_ENTRY_FROM_BUCKET = "(((long) bucket.value) << 32) | bucket.key"
        private const val BUCKET_KEY_FROM_ENTRY = "(int) entry"
        private const val DOC_COUNT_FROM_ENTRY = "(int) (entry >>> 32)"
        internal val SELECTED_ATTR_COMBINE_SCRIPT = """
            def shardSize = (int) (params.size * 1.5 + 10);
            def queue = new java.util.PriorityQueue();
            for (bucket in state.buckets.entrySet()) {
                queue.offer($QUEUE_ENTRY_FROM_BUCKET);
                if (queue.size() == shardSize) {
                    queue.poll();
                }
            }
            return queue.toArray();
        """.trimIndent()
        // Merge entries from all shards and then select `params.size` top entries.
        // Finally move entries into array and return them as a result of an aggregation.
        // When processing the result we should decompose entries into a bucket key and a count.
        // Note returned entries are not sorted because `Arrays.sort` method is missing in Painless
        // so they should be sorted on a client side.
        internal val SELECTED_ATTR_REDUCE_SCRIPT = """
            def buckets = new HashMap();
            for (state in states) {
                for (entry in state) {
                    buckets.compute(
                        $BUCKET_KEY_FROM_ENTRY,
                        (_, docCount) -> {
                            if (docCount == null) {
                                return $DOC_COUNT_FROM_ENTRY;
                            } else {
                                return docCount + $DOC_COUNT_FROM_ENTRY;
                            }
                        }
                    );
                }
            }

            def queue = new java.util.PriorityQueue();
            for (bucket in buckets.entrySet()) {
                queue.offer($QUEUE_ENTRY_FROM_BUCKET);
                if (queue.size() == params.size) {
                    queue.poll();
                }
            }

            def result = new long[queue.size()];
            int i = queue.size() - 1;
            for (entry in queue) {
                result[i] = entry;
                i--;
            }
            return result;
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

        for ((attrId, selectedAttrValues) in selectedValues) {
            // For an intersect mode we don't need a dedicated aggregation:
            // values and counts will be taken from a common aggregation
            if (selectedAttrValues.mode == FilterMode.INTERSECT) {
                continue
            }
            val otherAttrFacetFilterExpressions = selectedValues
                .mapNotNull { (a, w) ->
                    if (w.mode == FilterMode.INTERSECT || a != attrId) {
                        w.filterExpression(filter.field)
                    } else {
                        null
                    }
                }
            val attrAgg = ScriptedMetricAgg(
                SimpleListType(LongType),
                initScript = Script.Source(SELECTED_ATTR_INIT_SCRIPT),
                mapScript = Script.Source(SELECTED_ATTR_MAP_SCRIPT),
                combineScript = Script.Source(SELECTED_ATTR_COMBINE_SCRIPT),
                reduceScript = Script.Source(SELECTED_ATTR_REDUCE_SCRIPT),
                params = mapOf(
                    "attrId" to attrId,
                    "attrsField" to filter.field,
                    "size" to DEFAULT_ATTR_AGG_SIZE,
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
                    Bool.filter(otherFacetFilterExpressions),
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

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrFacetFilterResult {
        val facetValues = mutableMapOf<Int, MutableList<AttrFacetValue>>()

        val aggsResult = searchQueryResult.unwrapFilterAgg(otherFilterAggName)

        val fullAgg = aggsResult.facetAgg<TermsAggResult<Long>>(fullAggName)
        for (bucket in fullAgg.buckets) {
            val (attrId, valueId) = decodeAttrAndValue(bucket.key)
            val values = selectedValues[attrId]
            if (values != null && values.mode != FilterMode.INTERSECT) {
                continue
            }
            facetValues.getOrPut(attrId, ::mutableListOf)
                .add(AttrFacetValue(valueId, bucket.docCount, false))
        }

        for ((attrId, selectedAttrValues) in selectedValues) {
            if (selectedAttrValues.mode == FilterMode.INTERSECT) {
                continue
            }
            val attrAgg = aggsResult.facetAgg<ScriptedMetricAggResult<*>>(attrAggName(attrId))
            facetValues[attrId] = (attrAgg.value as List<*>)
                .map { entry ->
                    val (docCount, valueId) = decodeAttrAndValue(entry as Long)
                    AttrFacetValue(valueId, docCount.toLong(), valueId in selectedAttrValues.valueIds)
                }
                // Sort by count descending and then by value ascending
                .sortedByDescending { fv ->
                    fv.count shl Int.SIZE_BITS or (fv.value.inv().toLong() and INT_MASK)
                }
                .toMutableList()
        }

        return AttrFacetFilterResult(
            name,
            paramName,
            facets = facetValues.mapValues { (attrId, values) ->
                AttrFacet(attrId, values)
            }
        )
    }
}

data class AttrFacetFilterResult(
    override val name: String,
    override val paramName: String,
    val facets: Map<Int, AttrFacet>
) : FilterResult, Iterable<Map.Entry<Int, AttrFacet>> by facets.entries

data class AttrFacet(
    val attrId: Int,
    val values: List<AttrFacetValue>,
) : Iterable<AttrFacetValue> by values

data class AttrFacetValue(
    val value: Int,
    val count: Long,
    val selected: Boolean,
)
