package samples.document.field

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.types.BooleanType
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.KeywordType
import dev.evo.elasticmagic.types.TextType

object UserDoc : Document() {
    // It is possible to pass field options via shortcuts like `index`
    // or specifying `params` argument which can include any options (see `about` field below)
    val id by field(IntType, index = false, store = true)

    val login by field(KeywordType)

    // By default the field name is equal to the property name
    // but that behaviour can be changed
    val isAdmin by field("is_admin", BooleanType)

    val about by field(TextType, params = mapOf("norms" to false))
}
