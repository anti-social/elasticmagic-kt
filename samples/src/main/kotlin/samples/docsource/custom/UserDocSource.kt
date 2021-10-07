package samples.docsource.custom

import dev.evo.elasticmagic.DocSource

import samples.docsource.UserDoc

// Explicit types are specified for clarity. You can totally omit them

class RoleDocSource : DocSource() {
    var name: String by UserDoc.roles.name.required()
    var permissions: List<String> by UserDoc.roles.permissions.required().list().required()
}

class UserDocSource : DocSource() {
    // id and login fields must be present
    var id: Int by UserDoc.id.required()
    var login: String by UserDoc.login.required()

    // If groups field is missing or null default value will be used
    var groups: List<String> by UserDoc.groups.required().list().default { emptyList() }

    // Optional list of a required RoleDocSource instances
    var roles: List<RoleDocSource>? by UserDoc.roles.source(::RoleDocSource).required().list()
}

val nobody = UserDocSource().apply {
    id = 65535
    login = "nobody"
}

val nobodyHasGroups = nobody.groups.isEmpty()

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

val rootId: Int = root.id

val rootPermissions: List<String>? = root.roles
    ?.flatMap {
        it.permissions
    }
