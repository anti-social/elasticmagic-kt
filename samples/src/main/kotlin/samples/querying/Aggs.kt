package samples.querying

import dev.evo.elasticmagic.aggs.AvgAgg
import dev.evo.elasticmagic.aggs.HistogramAgg
import dev.evo.elasticmagic.aggs.TermsAgg

// Build aggregations by groups and calculate average rating as well as
// build histogram by rating for every group
val ratingHistogramQuery = q.aggs(
    "groups" to TermsAgg(
        UserDoc.groups,
        aggs = mapOf(
            "avg_rating" to AvgAgg(UserDoc.rating),
            "rating_histogram" to HistogramAgg(
                UserDoc.rating, interval = 10.0F
            ),
        )
    )
)
