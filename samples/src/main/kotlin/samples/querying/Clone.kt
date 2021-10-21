package samples.querying

import dev.evo.elasticmagic.query.match

val clonedQuery = q.clone()
    .query(UserDoc.about.match("fake"))
