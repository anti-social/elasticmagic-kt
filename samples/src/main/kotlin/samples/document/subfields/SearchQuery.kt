package samples.document.subfields

import dev.evo.elasticmagic.SearchQuery

val maybeNiceUsers = SearchQuery(UserDoc.about.autocomplete.match("nic"))
    .sort(UserDoc.about.sort)
