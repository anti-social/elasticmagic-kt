package samples.docsource.dynamic

import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.list

import samples.docsource.UserDoc

val root = DynDocSource {
    it[UserDoc.id] = 0
    it[UserDoc.login] = "root"
    it[UserDoc.groups.list()] = mutableListOf("root", "wheel")
    it[UserDoc.roles.list()] = mutableListOf(
        DynDocSource {
            it[UserDoc.roles.name] = "superuser"
            it[UserDoc.roles.permissions.list()] = mutableListOf("*")
        }
    )
}

// Int?
val rootId = root[UserDoc.id]

// List<String?>?
val rootPermissions = root[UserDoc.roles.list()]
    ?.mapNotNull {
        it?.get(UserDoc.roles.permissions.list())
    }
    ?.flatten()
