package samples.docsource

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.SubDocument

class RoleDoc : SubDocument() {
    val name by keyword()
    val permissions by keyword()
}

object UserDoc : Document() {
    val id by int()
    val login by keyword()
    val groups by keyword()
    val roles by nested(::RoleDoc)
}
