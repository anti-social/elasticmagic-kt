package samples.document.enums

import dev.evo.elasticmagic.SearchQuery

val q = SearchQuery()
    .filter(
        UserDoc.status.eq(UserStatus.ACTIVE)
    )
