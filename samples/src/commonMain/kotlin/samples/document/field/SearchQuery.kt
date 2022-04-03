package samples.document.field

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.query.match

val fakeAdmins = SearchQuery(UserDoc.about.match("fake"))
    .filter(UserDoc.isAdmin.eq(true))
    .sort(UserDoc.id)
