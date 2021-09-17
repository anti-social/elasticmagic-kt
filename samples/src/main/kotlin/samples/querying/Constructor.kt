package samples.querying

import dev.evo.elasticmagic.FunctionScore
import dev.evo.elasticmagic.SearchQuery

var q1 = SearchQuery(
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
