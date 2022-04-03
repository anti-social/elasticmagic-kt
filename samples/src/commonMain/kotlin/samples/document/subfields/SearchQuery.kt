package samples.document.subfields

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.query.match

val maybeNiceUsers = SearchQuery(UserDoc.about.autocomplete.match("nic"))
    .sort(UserDoc.about.sort)
