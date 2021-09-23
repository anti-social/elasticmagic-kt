package samples.document.meta

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.MetaFields

object ProductDoc : Document() {
    val name by text()
    val companyId by int()

    override val meta = object : MetaFields() {
        override val routing by RoutingField(required = true)
        override val source by SourceField(includes = listOf("name"))
    }
}
