package samples.querying

import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.SearchQuery

var q = SearchQuery(
    FunctionScore(
        UserDoc.about.match("fake"),
        listOf(
            FunctionScore.FieldValueFactor(
                UserDoc.rating,
                missing = 0.0,
            )
        )
    )
)
