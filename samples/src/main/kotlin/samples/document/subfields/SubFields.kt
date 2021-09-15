package samples.document.subfields

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.SubFields

class AboutSubFields : SubFields<String>() {
    val sort by keyword(normalizer = "lowercase")
    val autocomplete by text(analyzer = "autocomplete")
}

object UserDoc : Document() {
    val about by text().subFields(::AboutSubFields)
}
