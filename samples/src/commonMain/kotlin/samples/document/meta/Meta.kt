package samples.document.meta

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.MetaFields

object ProductDoc : Document() {
    val name by text()
    val companyId by int()

    override val meta = object : MetaFields() {
        override val routing by RoutingField(required = true)
        override val source by SourceField(includes = listOf("name"))
    }
}
