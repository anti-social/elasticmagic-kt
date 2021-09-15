package samples.document.`object`

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SubDocument

class GroupDoc : SubDocument() {
    val id by int()
    val name by keyword()
}

object UserDoc : Document() {
    val groups by obj(::GroupDoc)
}

val systemUsers = SearchQuery()
    .filter(UserDoc.groups.name.eq("system"))
