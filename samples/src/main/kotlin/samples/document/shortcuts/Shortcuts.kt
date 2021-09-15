package samples.document.shortcuts

import dev.evo.elasticmagic.Document

object UserDoc : Document() {
    val id by int(index = false, store = true)
    val login by keyword()
    val isAdmin by boolean("is_admin")
    val about by text(norms = false)
}
