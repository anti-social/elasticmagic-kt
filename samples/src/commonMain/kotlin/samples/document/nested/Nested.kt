package samples.document.nested

import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.ObjectBoundField
import dev.evo.elasticmagic.query.Nested
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.doc.SubDocument

class RoleDoc(field: ObjectBoundField) : SubDocument(field) {
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
