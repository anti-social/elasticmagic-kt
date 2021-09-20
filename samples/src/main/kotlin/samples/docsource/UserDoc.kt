package samples.docsource

import dev.evo.elasticmagic.BaseDocSource
import dev.evo.elasticmagic.BoundField
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.SubDocument

class RoleDoc(field: BoundField<BaseDocSource>) : SubDocument(field) {
    val name by keyword()
    val permissions by keyword()
}

object UserDoc : Document() {
    val id by int()
    val login by keyword()
    val groups by keyword()
    val roles by nested(::RoleDoc)
}
