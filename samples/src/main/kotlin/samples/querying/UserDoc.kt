package samples.querying

import dev.evo.elasticmagic.Document

object UserDoc : Document() {
    val id by int()
    val rating by float()
    val about by text()
}
