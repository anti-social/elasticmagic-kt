package samples.docsource.custom

import dev.evo.elasticmagic.doc.DocSource

import samples.docsource.UserDoc

// Explicit types are specified for clarity. You can totally omit them

class RoleDocSource : DocSource() {
    var name: String by UserDoc.roles.name.required()
    var permissions: MutableList<String> by UserDoc.roles.permissions.required().list().required()
}

class UserDocSource : DocSource() {
    // id and login fields must be present
    var id: Int by UserDoc.id.required()
    var login: String by UserDoc.login.required()

    // If groups field is missing or null default value will be used
    var groups: MutableList<String> by UserDoc.groups.required().list().default { mutableListOf() }

    // Optional list of a required RoleDocSource instances
    var roles: MutableList<RoleDocSource>? by UserDoc.roles.source(::RoleDocSource).required().list()
}

val nobody = UserDocSource().apply {
    id = 65535
    login = "nobody"
}

val nobodyHasGroups = nobody.groups.isEmpty()

val root = UserDocSource().apply {
    id = 0
    login = "root"
    groups = mutableListOf("root", "wheel")
    roles = mutableListOf(
        RoleDocSource().apply {
            name = "superuser"
            permissions = mutableListOf("*")
        }
    )
}

val rootId: Int = root.id

val rootPermissions: List<String>? = root.roles
    ?.flatMap {
        it.permissions
    }
