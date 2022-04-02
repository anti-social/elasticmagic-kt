package samples.code.SearchQuery

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.HistogramAgg
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.FunctionScoreNode
import dev.evo.elasticmagic.query.MatchPhrase
import dev.evo.elasticmagic.query.MultiMatch
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryRescore
import dev.evo.elasticmagic.query.match

import kotlin.random.Random

object UserDoc : Document() {
    val id by int()
    val name by text()
    val about by text()
    val rank by float()
    val isActive by boolean()
}

val searchQuery = SearchQuery()

fun query() {
    searchQuery.query(MultiMatch("system", listOf(UserDoc.name, UserDoc.about)))
    searchQuery.query(null)
}

fun queryNode() {
    val BOOST_HANDLE = NodeHandle<FunctionScoreNode>()

    searchQuery.query(
        FunctionScoreNode(
            BOOST_HANDLE,
            query = MultiMatch("system", listOf(UserDoc.name, UserDoc.about)),
            functions = listOf(
                FunctionScore.FieldValueFactor(
                    UserDoc.rank,
                    missing = 1.0F,
                    factor = 2.0,
                    modifier = FunctionScore.FieldValueFactor.Modifier.LN1P
                )
            ),
            scoreMode = FunctionScore.ScoreMode.SUM,
        )
    )

    val boostActive = Random.nextBoolean()
    if (boostActive) {
        // Move active users the top
        searchQuery.queryNode(BOOST_HANDLE) {
            it.functions.add(
                FunctionScore.Weight(
                    1000.0,
                    filter = UserDoc.isActive.eq(true)
                )
            )
        }
    }
}

fun filter() {
    searchQuery.filter(
        UserDoc.isActive.eq(true),
        Bool.should(
            UserDoc.isActive.eq(false),
            UserDoc.name.match("system"),
        )
    )
}

fun postFilter() {
    // Calculate aggregation for all users but fetch only active
    searchQuery
        .aggs(
            "is_active" to TermsAgg(UserDoc.isActive)
        )
        .postFilter(
            UserDoc.isActive.eq(true)
        )
}

fun aggs() {
    // Calculate a histogram for user rank and for every bucket in the histogram
    // count active and non-active users
    searchQuery.aggs(
        "rank_hist" to HistogramAgg(
            UserDoc.rank,
            interval = 1.0F,
            aggs = mapOf(
                "is_active" to TermsAgg(UserDoc.isActive)
            )
        )
    )
}

fun rescore() {
    // Boost top 100 users that match a given phrase
    searchQuery.rescore(
        QueryRescore(
            MatchPhrase(
                UserDoc.about,
                "brown fox",
                slop = 2,
            ),
            windowSize = 100,
            rescoreQueryWeight = 2.0,
        )
    )
}

fun sort() {
    // Sort query by a document score and a user id
    searchQuery.sort(UserDoc.runtime.score, UserDoc.id.desc())
}
