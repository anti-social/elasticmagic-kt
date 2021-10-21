package samples.querying

import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.match

val q2 = q.query(
    FunctionScore(
        Bool.should(
            UserDoc.about.match("fake"),
            UserDoc.about.match("real"),
        ),
        listOf(
            FunctionScore.FieldValueFactor(
                UserDoc.rating,
                missing = 0.0F,
            )
        )
    )
)
