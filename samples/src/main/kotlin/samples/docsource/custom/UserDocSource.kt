package samples.docsource.custom

import dev.evo.elasticmagic.DocSource

import samples.docsource.UserDoc

class RoleDocSource : DocSource() {
    var name by UserDoc.roles.name.required()
    var permissions by UserDoc.roles.permissions.required().list().required()
}

class UserDocSource : DocSource() {
    var id by UserDoc.id.required()
    var login by UserDoc.login.required()
    var groups by UserDoc.groups.required().list().required()
    var roles by UserDoc.roles.source(::RoleDocSource).required().list()
}

val root = UserDocSource().apply {
    id = 0
    login = "root"
    groups = listOf("root", "wheel")
    roles = listOf(
        RoleDocSource().apply {
            name = "superuser"
            permissions = listOf("*")
        }
    )
}

// Int
val rootId = root.id

// List<String>?
val rootPermissions = root.roles
    ?.flatMap {
        it.permissions
    }
