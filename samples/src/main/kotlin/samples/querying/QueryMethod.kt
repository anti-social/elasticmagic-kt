package samples.querying

import dev.evo.elasticmagic.Bool
import dev.evo.elasticmagic.FunctionScore

val q2 = q.query(
    FunctionScore(
        Bool.should(
            UserDoc.about.match("fake"),
            UserDoc.about.match("real"),
        ),
        listOf(
            FunctionScore.FieldValueFactor(
                UserDoc.rating,
                missing = 0.0,
            )
        )
    )
)
