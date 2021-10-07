package samples.document.subfields.mistake

import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.SubFields

class AboutSubFields(field: BoundField<String>) : SubFields<String>(field) {
    val sort by keyword(normalizer = "lowercase")
}

object UserDoc : Document() {
    val about by text().subFields(::AboutSubFields)
    val description by text().subFields { about }
}

fun main() {
    println(UserDoc)
}
