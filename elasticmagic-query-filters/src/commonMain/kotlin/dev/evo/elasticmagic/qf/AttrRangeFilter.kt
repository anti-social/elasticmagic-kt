package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.ScriptedMetricAgg
import dev.evo.elasticmagic.aggs.ScriptedMetricAggResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.types.deErr
import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.SimpleListType
import dev.evo.elasticmagic.types.SimpleFieldType

private const val INT_MASK: Long = 0x00000000_ffffffffL

fun encodeRangeAttrWithValue(attrId: Int, value: Float): Long {
    return (attrId.toLong() shl Int.SIZE_BITS) or (value.toBits().toLong() and INT_MASK)
}

private fun getAttrRangeFacetSelectedValues(params: QueryFilterParams, paramName: String) =
    buildMap<_, AttrRangeFacetFilter.SelectedValue> {
        for ((keys, values) in params) {
            @Suppress("MagicNumber")
            when {
                keys.isEmpty() -> {}
                values.isEmpty() -> {}
                keys[0] != paramName -> {}
                keys.size == 3 -> {
                    val attrId = IntType.deserializeTermOrNull(keys[1]) ?: continue
                    val value = FloatType.deserializeTermOrNull(values.last()) ?: continue
                    when (keys[2]) {
                        "gte" -> {
                            val selectedValue =
                                getOrPut(attrId) { AttrRangeFacetFilter.SelectedValue.Gte(attrId, value) }
                            if (selectedValue is AttrRangeFacetFilter.SelectedValue.Lte) {
                                put(attrId, selectedValue.gte(value))
                            } else {
                                put(attrId, selectedValue)
                            }
                        }

                        "lte" -> {
                            val selectedValue =
                                getOrPut(attrId) { AttrRangeFacetFilter.SelectedValue.Lte(attrId, value) }
                            if (selectedValue is AttrRangeFacetFilter.SelectedValue.Gte) {
                                put(attrId, selectedValue.lte(value))
                            } else {
                                put(attrId, selectedValue)
                            }
                        }

                        else -> {}
                    }
                }

                else -> {}
            }
        }
    }

class AttrRangeFacetFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<AttrRangeFacetFilterResult>(name) {

    /**
     *
     *             -Inf                 +0.0
     *    0x{attr_id}_ff800000 0x{attr_id}_00000000
     *                       | |
     *                       ***
     *                    **     **
     *                 **           **
     *                *               *
     *    negative   *                 *   positive
     *    floats ⤹   *                 *  ⤸ floats
     *                *               *
     *                 **           **
     *                    **     **
     *                       ***
     *                       |||
     *    0x{attr_id}_80000000|0x{attr_id}_7f800000
     *           -0.0         |            +Inf
     *                0x{attr_id}_7fc00000
     *                       NaN
     */
    sealed class SelectedValue {
        abstract val attrId: Int

        abstract fun filterExpression(field: FieldOperations<Long>): QueryExpression

        data class Gte(override val attrId: Int, val gte: Float) : SelectedValue() {
            fun lte(lte: Float): SelectedValue {
                return Between(attrId, gte, lte)
            }

            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                return when {
                    gte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, -0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.POSITIVE_INFINITY)
                        )
                    )

                    gte > 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, gte),
                        lte = encodeRangeAttrWithValue(attrId, Float.POSITIVE_INFINITY)
                    )

                    else -> Bool.should(
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, gte)
                        ),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.POSITIVE_INFINITY)
                        ),
                    )
                }
            }
        }

        data class Lte(override val attrId: Int, val lte: Float) : SelectedValue() {
            fun gte(gte: Float): SelectedValue {
                return Between(attrId, gte, lte)
            }

            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                return when {
                    lte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, 0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY),
                        )
                    )

                    lte < 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, lte),
                        lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY)
                    )

                    else -> Bool.should(
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, lte),
                        ),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY)
                        ),
                    )
                }
            }
        }

        data class Between(override val attrId: Int, val gte: Float, val lte: Float) : SelectedValue() {
            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                return when {
                    gte == 0.0F && lte == 0.0F -> field.oneOf(
                        encodeRangeAttrWithValue(attrId, 0.0F),
                        encodeRangeAttrWithValue(attrId, -0.0F),
                    )

                    gte > lte -> Bool.filter(
                        Gte(attrId, gte).filterExpression(field),
                        Lte(attrId, lte).filterExpression(field),
                    )

                    gte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, -0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, lte)
                        ),
                    )

                    lte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, 0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, gte)
                        ),
                    )

                    gte > 0.0F && lte > 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, gte),
                        lte = encodeRangeAttrWithValue(attrId, lte)
                    )

                    gte < 0.0F && lte < 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, lte),
                        lte = encodeRangeAttrWithValue(attrId, gte)
                    )

                    else -> Bool.should(
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, lte)
                        ),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, gte)
                        ),
                    )
                }
            }
        }
    }

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedAttrRangeFacetFilter {
        val selectedValues = getAttrRangeFacetSelectedValues(params, paramName)

        val facetFilters = selectedValues.values.map { w ->
            w.filterExpression(field)
        }
        val facetFilterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrRangeFacetFilter(this, name, paramName, facetFilterExpr, selectedValues)
    }
}

class PreparedAttrRangeFacetFilter(
    val filter: AttrRangeFacetFilter,
    name: String,
    paramName: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: Map<Int, AttrRangeFacetFilter.SelectedValue>
) : PreparedFilter<AttrRangeFacetFilterResult>(name, paramName, facetFilterExpr) {

    private val filterAggName = "qf:$name.filter"
    private val attrsAggName = "qf:$name.full"
    private val filterAttrsAggName = "qf:$name.full.filter"
    private fun attrAggName(attrId: Int) = "qf:$name.$attrId"
    private fun filterAttrAggName(attrId: Int) = "qf:$name.$attrId.filter"

    companion object {
        const val DEFAULT_ATTRS_AGG_SIZE = 100
        internal val ATTRS_INIT_SCRIPT = """
            state.attrs = new HashMap();
        """.trimIndent()
        internal val ATTRS_MAP_SCRIPT = """
            if (doc[params.attrsField].size() == 0) {
                return;
            }
            for (v in doc[params.attrsField]) {
                int attrId = (int) (v >>> 32);
                int valueBits = (int) (v & 0xffffffffL);
                float value = Float.intBitsToFloat(valueBits);
                int[] attrData = state.attrs.get(attrId);
                if (attrData == null) {
                    state.attrs[attrId] = new int[] { attrId, 1, valueBits, valueBits };
                } else {
                    attrData[1]++;
                    float min = Float.intBitsToFloat(attrData[2]);
                    float max = Float.intBitsToFloat(attrData[3]);
                    if (value < min) {
                       attrData[2] = valueBits;
                    } else if (value > max) {
                      attrData[3] = valueBits;
                    }
                }
            }
        """.trimIndent()
        internal val ATTRS_COMBINE_SCRIPT = """
            def shardSize = (int) (params.size * 1.5 + 10);
            def queue = new java.util.PriorityQueue((a1, a2) -> {
                // Sort by a doc count descending
                int r = a1[1] - a2[1];
                if (r == 0) {
                    // Then by attribute id ascending
                    return a2[0] - a1[0];
                } else {
                    return r;
                }
            });
            state.attrs.forEach((attrId, attrData) -> {
                if (queue.size() == shardSize) {
                    queue.poll();
                }
                queue.offer(attrData);
            });
            return queue.toArray();
        """.trimIndent()
        internal val ATTRS_REDUCE_SCRIPT = """
            def attrs = new HashMap();
            for (state in states) {
                for (attrData in state) {
                    int[] curAttrData = attrs.get(attrData[0]);
                    if (curAttrData == null) {
                        attrs[attrData[0].toString()] = attrData;
                    } else {
                        curAttrData[1] += attrData[1];
                        float min = Float.intBitsToFloat(attrData[2]);
                        float max = Float.intBitsToFloat(attrData[3]);
                        float curMin = Float.intBitsToFloat(curAttrData[2]);
                        float curMax = Float.intBitsToFloat(curAttrData[3]);
                        if (min < curMin) {
                            curAttrData[2] = attrData[2];
                        }
                        if (max < curMax) {
                            curAttrData[3] = attrData[3];
                        }
                    }
                }
            }

            def queue = new java.util.PriorityQueue((a1, a2) -> {
                // Sort by a doc count descending
                int r = a1[1] - a2[1];
                if (r == 0) {
                    // Then by attribute id ascending
                    return a2[0] - a1[0];
                } else {
                    return r;
                }
            });
            attrs.forEach((attrId, attrData) -> {
                if (queue.size() == params.size) {
                    queue.poll();
                }
                queue.offer(attrData);
            });

            def result = new HashMap[queue.size()];
            int i = queue.size() - 1;
            while (queue.size() > 0) {
                def attrData = queue.poll();
                def r = new HashMap();
                r["attr_id"] = attrData[0];
                r["count"] = attrData[1];
                r["min"] = Float.intBitsToFloat(attrData[2]);
                r["max"] = Float.intBitsToFloat(attrData[3]);
                result[i] = r;
                i--;
            }
            return result;
        """.trimIndent()

        internal val SINGLE_ATTR_INIT_SCRIPT = """
            state.count = 0L;
            state.min = null;
            state.max = null;
        """.trimIndent()
        internal val SINGLE_ATTR_MAP_SCRIPT = """
            if (doc[params.attrsField].size() == 0) {
                return;
            }
            boolean foundAttr = false;
            for (v in doc[params.attrsField]) {
                int attrId = (int) (v >>> 32);
                if (attrId != params.attrId) {
                    continue;
                }
                foundAttr = true;
                int valueBits = (int) (v & 0xffffffffL);
                float value = Float.intBitsToFloat(valueBits);
                if (state.min == null || value < state.min) {
                    state.min = value;
                } else if (state.max == null || value > state.max) {
                    state.max = value;
                }
            }
            if (foundAttr) {
                state.count++;
            }
        """.trimIndent()
        internal val SINGLE_ATTR_COMBINE_SCRIPT = """
            state.attrId = params.attrId;
            return state;
        """.trimIndent()
        internal val SINGLE_ATTR_REDUCE_SCRIPT = """
            Integer attrId = null;
            long count = 0L;
            Float min = null;
            Float max = null;
            for (state in states) {
                attrId = state.attrId;
                count += state.count;
                if (state.min != null && (min == null || state.min < min)) {
                    min = state.min;
                }
                if (state.min != null && (max == null || state.max > max)) {
                    max = state.max;
                }
            }

            def result = new HashMap();
            result.put("attr_id", attrId);
            result.put("count", count);
            result.put("min", min);
            result.put("max", max);
            return result;
        """.trimIndent()
    }

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val aggs = mutableMapOf<String, Aggregation<*>>()
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
                AttrRangeFacetType,
                initScript = Script.Source(SINGLE_ATTR_INIT_SCRIPT),
                mapScript = Script.Source(SINGLE_ATTR_MAP_SCRIPT),
                combineScript = Script.Source(SINGLE_ATTR_COMBINE_SCRIPT),
                reduceScript = Script.Source(SINGLE_ATTR_REDUCE_SCRIPT),
                params = Params(
                    "attrId" to attrId,
                    "attrsField" to filter.field,
                )
            )
            if (otherAttrFacetFilterExpressions.isNotEmpty()) {
                aggs[filterAttrAggName(attrId)] = FilterAgg(
                    maybeWrapBool(Bool::filter, otherAttrFacetFilterExpressions),
                    aggs = mapOf(
                        attrAggName(attrId) to attrAgg
                    )
                )
            } else {
                aggs[attrAggName(attrId)] = attrAgg
            }
        }

        val fullAttrsAgg = ScriptedMetricAgg(
            SimpleListType(AttrRangeFacetType),
            initScript = Script.Source(ATTRS_INIT_SCRIPT),
            mapScript = Script.Source(ATTRS_MAP_SCRIPT),
            combineScript = Script.Source(ATTRS_COMBINE_SCRIPT),
            reduceScript = Script.Source(ATTRS_REDUCE_SCRIPT),
            params = Params(
                "attrsField" to filter.field,
                "size" to DEFAULT_ATTRS_AGG_SIZE,
            )
        )
        if (facetFilterExpr != null) {
            aggs[filterAttrsAggName] = FilterAgg(
                facetFilterExpr,
                aggs = mapOf(attrsAggName to fullAttrsAgg)
            )
        } else {
            aggs[attrsAggName] = fullAttrsAgg
        }


        searchQuery.aggs(
            if (otherFacetFilterExpressions.isNotEmpty()) {
                mapOf(
                    filterAggName to FilterAgg(
                        maybeWrapBool(Bool::filter, otherFacetFilterExpressions),
                        aggs = aggs
                    )
                )
            } else {
                aggs
            }
        )

        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrRangeFacetFilterResult {
        val facets = mutableMapOf<Int, AttrRangeFacet>()

        val aggsResult = searchQueryResult.unwrapFilterAgg(filterAggName)

        val attrsAgg = aggsResult.facetAgg<ScriptedMetricAggResult<List<AttrRangeFacet>>>(attrsAggName)
        for (bucket in attrsAgg.value) {
            facets[bucket.attrId] = AttrRangeFacet(
                bucket.attrId, bucket.count, bucket.min, bucket.max
            )
        }

        for (attrId in selectedValues.keys) {
            val attrAgg = aggsResult.facetAgg<ScriptedMetricAggResult<AttrRangeFacet>>(attrAggName(attrId))
            facets[attrId] = attrAgg.value
        }

        return AttrRangeFacetFilterResult(name, paramName, facets)
    }
}

object AttrRangeFacetType : SimpleFieldType<AttrRangeFacet>() {
    override val name
        get() = throw IllegalStateException("Should not be used in mappings")
    override val termType = AttrRangeFacet::class

    override fun deserialize(v: Any): AttrRangeFacet {
        if (v !is Deserializer.ObjectCtx) {
            deErr(v, "Object")
        }
        return AttrRangeFacet(
            attrId = v.int("attr_id"),
            count = v.long("count"),
            min = v.floatOrNull("min"),
            max = v.floatOrNull("max"),
        )
    }
}

class AttrRangeSimpleFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<BaseFilterResult>(name) {

    override fun prepare(
        name: String,
        paramName: String,
        params: QueryFilterParams
    ): PreparedAttrRangeExpressionFilter {
        val facetFilters = getAttrRangeFacetSelectedValues(params, paramName)
            .values.map { w ->
                w.filterExpression(field)
            }

        val filterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrRangeExpressionFilter(this, name, paramName, filterExpr)
    }
}

class PreparedAttrRangeExpressionFilter(
    val filter: AttrRangeSimpleFilter,
    name: String,
    paramName: String,
    filterExpr: QueryExpression?,
) : PreparedFilter<BaseFilterResult>(name, paramName, filterExpr) {

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        if (facetFilterExpr != null) {
            searchQuery.filter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): BaseFilterResult {
        return BaseFilterResult(
            name,
            paramName
        )
    }
}

data class AttrRangeFacetFilterResult(
    override val name: String,
    override val paramName: String,
    val facets: Map<Int, AttrRangeFacet>,
) : FilterResult

data class AttrRangeFacet(
    val attrId: Int,
    val count: Long,
    val min: Float?,
    val max: Float?,
)
