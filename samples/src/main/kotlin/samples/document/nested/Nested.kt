package samples.document.nested

import dev.evo.elasticmagic.BaseDocSource
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.BoundField
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.query.Nested
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SubDocument

class RoleDoc(field: BoundField<BaseDocSource>) : SubDocument(field) {
    val name by keyword()
    val permissions by keyword()
}

object UserDoc : Document() {
    val roles by nested(::RoleDoc)
}

val moderators = SearchQuery()
    .filter(
        Nested(
            UserDoc.roles,
            Bool.must(
                UserDoc.roles.name.eq("moderator"),
                UserDoc.roles.permissions.eq("article"),
                UserDoc.roles.permissions.eq("order"),
            )
        )
    )