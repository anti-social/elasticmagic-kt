package samples.document.subfields.mistake

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.SubFields

object AboutSubFields : SubFields<String>() {
    val sort by keyword(normalizer = "lowercase")
    val autocomplete by text(analyzer = "autocomplete")
}

object UserDoc : Document() {
    val about by text().subFields { AboutSubFields }
    val description by text().subFields { AboutSubFields }
}

fun main() {
    println(UserDoc)
}
