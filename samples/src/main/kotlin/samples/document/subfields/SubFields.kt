package samples.document.subfields

import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.SubFields

class AboutSubFields(field: BoundField<String>) : SubFields<String>(field) {
    val sort by keyword(normalizer = "lowercase")
    val autocomplete by text(analyzer = "autocomplete")
}

object UserDoc : Document() {
    val about by text().subFields(::AboutSubFields)
}
