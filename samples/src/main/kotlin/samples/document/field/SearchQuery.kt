package samples.document.field

import dev.evo.elasticmagic.SearchQuery

val fakeAdmins = SearchQuery(UserDoc.about.match("fake"))
    .filter(UserDoc.isAdmin.eq(true))
    .sort(UserDoc.id)
