package samples.querying

import dev.evo.elasticmagic.query.Bool

// Select only active users that id is not equal 0
val activeUsersQuery = q
    .filter(UserDoc.isActive.eq(true))
    .filter(UserDoc.id.ne(0))

// The same as above
val activeUsersQuery2 = q
    .filter(
        UserDoc.isActive.eq(true),
        UserDoc.id.ne(0)
    )

// If you need one condition OR another use Bool.should expression.
// In the following example we select non-active users OR "fake" users
val activeUsersQuery3 = q
    .filter(
        Bool.should(
            UserDoc.isActive.ne(true),
            UserDoc.about.match("fake")
        )
    )
