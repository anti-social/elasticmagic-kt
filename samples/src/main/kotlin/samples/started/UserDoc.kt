package samples.started

import dev.evo.elasticmagic.doc.Document

object UserDoc : Document() {
    val id by int()
    val name by keyword()
    val groups by keyword()
    val about by text()
}
