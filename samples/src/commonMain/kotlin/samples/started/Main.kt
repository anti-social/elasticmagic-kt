package samples.started

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.query.match

import kotlinx.coroutines.runBlocking

fun printUsers(result: SearchQueryResult<DynDocSource>) {
    println("Found: ${result.totalHits} users")
    for (hit in result.hits) {
        val user = hit.source!!
        println("  ${user[UserDoc.id]}: ${user[UserDoc.name]}")
    }
    println()
}

fun printGroupsAgg(aggResult: TermsAggResult<String>) {
    println("Groups aggregation")
    for (bucket in aggResult.buckets) {
        println("  ${bucket.key}: ${bucket.docCount}")
    }
}

fun main() = runBlocking {
    ensureIndexExists()
    indexDocs()

    // Find all users
    val sq = SearchQuery()
    printUsers(sq.search(userIndex))

    // Find nobody users
    sq.query(UserDoc.about.match("nobody"))
    printUsers(sq.search(userIndex))

    // Build an aggregation that counts users inside a group
    printGroupsAgg(
        SearchQuery()
            .aggs("groups" to TermsAgg(UserDoc.groups))
            .search(userIndex)
            .agg("groups")
    )
}
