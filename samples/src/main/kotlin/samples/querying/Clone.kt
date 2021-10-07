package samples.querying

val clonedQuery = q.clone()
    .query(UserDoc.about.match("fake"))
