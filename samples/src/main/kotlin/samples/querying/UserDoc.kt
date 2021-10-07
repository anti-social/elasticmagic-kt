package samples.querying

import dev.evo.elasticmagic.Document

object UserDoc : Document() {
    val id by int()
    val isActive by boolean("is_active")
    val groups by keyword()
    val rating by float()
    val about by text()
}
