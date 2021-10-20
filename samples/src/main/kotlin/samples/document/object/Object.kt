package samples.document.`object`

import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.doc.SubDocument

class GroupDoc(field: BoundField<BaseDocSource, Nothing>) : SubDocument(field) {
    val id by int()
    val name by keyword()
}

object UserDoc : Document() {
    val groups by obj(::GroupDoc)
}

val systemUsers = SearchQuery()
    .filter(UserDoc.groups.name.eq("system"))
