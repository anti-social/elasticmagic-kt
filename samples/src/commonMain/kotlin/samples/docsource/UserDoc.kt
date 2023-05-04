package samples.docsource

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.ObjectBoundField
import dev.evo.elasticmagic.doc.SubDocument

class RoleDoc(field: ObjectBoundField) : SubDocument(field) {
    val name by keyword()
    val permissions by keyword()
}

object UserDoc : Document() {
    val id by int()
    val login by keyword()
    val groups by keyword()
    val roles by nested(::RoleDoc)
}
