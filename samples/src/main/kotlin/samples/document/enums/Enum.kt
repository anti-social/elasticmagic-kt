package samples.document.enums

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.enum

enum class UserStatus {
    ACTIVE, LOCKED, NO_PASSWORD
}

object UserDoc : Document() {
    val status by keyword().enum<UserStatus>()
}
