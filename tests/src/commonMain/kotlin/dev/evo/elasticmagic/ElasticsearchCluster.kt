package dev.evo.elasticmagic

// import dev.evo.elasticmagic.compile.CompilerProvider
// import dev.evo.elasticmagic.transport.ElasticsearchTransport
// import dev.evo.elasticmagic.transport.Method
//
// import kotlinx.serialization.json.JsonArray
// import kotlinx.serialization.json.JsonObject
// import kotlinx.serialization.json.JsonPrimitive
// import kotlinx.serialization.json.boolean
// import kotlinx.serialization.json.contentOrNull
// import kotlinx.serialization.json.doubleOrNull
// import kotlinx.serialization.json.jsonArray
// import kotlinx.serialization.json.jsonObject
// import kotlinx.serialization.json.jsonPrimitive
// import kotlinx.serialization.json.long
//
// class ElasticsearchClusterImpl(
//     private val esTransport: ElasticsearchTransport,
//     private val compilerProvider: CompilerProvider<JsonObject, JsonArray>,
// ) : ElasticsearchCluster<JsonObject> {
//     override fun get(indexName: String): ElasticsearchIndexImpl {
//         return ElasticsearchIndexImpl(indexName, esTransport, compilerProvider)
//     }
// }
//
// class ElasticsearchIndexImpl(
//     override val indexName: String,
//     private val esTransport: ElasticsearchTransport,
//     private val compilerProvider: CompilerProvider<JsonObject, JsonArray>,
// ) : ElasticsearchIndex<JsonObject> {
//     override suspend fun <S : Source> search(
//         searchQuery: BaseSearchQuery<S, *>
//     ): SearchQueryResult<JsonObject, S> {
//         val preparedSearchQuery = searchQuery.prepare()
//         val compiled = compilerProvider.searchQuery.compile(searchQuery)
//         val result = esTransport.jsonRequest(
//             Method.GET, "$indexName/_doc/_search", body = compiled.body
//         ).jsonObject
//         val rawHitsData = result["hits"]?.jsonObject
//         val rawTotal = rawHitsData?.get("total")
//         val (totalHits, totalHitsRelation) = when (rawTotal) {
//             is JsonPrimitive -> rawTotal.long to null
//             is JsonObject -> {
//                 rawTotal["value"]?.jsonPrimitive?.long to rawTotal["relation"]?.jsonPrimitive?.content
//             }
//             null -> null to null
//             else -> error("Cannot parse total hits")
//         }
//         val hits = mutableListOf<SearchHit<S>>()
//         val rawHits = rawHitsData?.get("hits")?.jsonArray
//         if (!rawHits.isNullOrEmpty()) {
//             for (rawHitElement in rawHits) {
//                 val rawHit = rawHitElement.jsonObject
//                 val rawSource = rawHit["_source"]?.jsonObject
//                 val source = if (rawSource != null) {
//                     val source = preparedSearchQuery.sourceFactory()
//                     for ((fieldName, fieldValue) in rawSource) {
//                         source.setField(fieldName, fieldValue)
//                     }
//                     source
//                 } else {
//                     null
//                 }
//                 hits.add(
//                     SearchHit(
//                         index = rawHit["_index"]?.jsonPrimitive?.content!!,
//                         type = rawHit["_type"]?.jsonPrimitive?.content!!,
//                         id = rawHit["_id"]?.jsonPrimitive?.content!!,
//                         score = rawHit["_score"]?.jsonPrimitive?.doubleOrNull,
//                         source = source,
//                     )
//                 )
//             }
//         }
//         return SearchQueryResult(
//             null,
//             took = result["took"]?.jsonPrimitive?.long!!,
//             timedOut = result["timed_out"]?.jsonPrimitive?.boolean!!,
//             totalHits = totalHits,
//             totalHitsRelation = totalHitsRelation,
//             maxScore = rawHitsData?.get("max_score")?.jsonPrimitive?.doubleOrNull,
//             hits = hits,
//         )
//     }
// }
