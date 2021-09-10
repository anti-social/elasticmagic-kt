package samples.started

import dev.evo.elasticmagic.Document

object UserDoc : Document() {
    val id by int()
    val name by keyword()
    val about by text()
}
