package samples.document.field

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.BooleanType
import dev.evo.elasticmagic.IntType
import dev.evo.elasticmagic.KeywordType
import dev.evo.elasticmagic.TextType

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
