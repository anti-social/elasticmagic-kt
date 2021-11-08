package samples.started

import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.IndexAction
import dev.evo.elasticmagic.doc.IdentActionMeta
import dev.evo.elasticmagic.doc.list
import dev.evo.elasticmagic.doc.Refresh

suspend fun indexDocs() {
    val docs = listOf(
        DynDocSource {
            // Note that you can't write like following (it just won't compile):
            // it[UserDoc.id] = "0"
            // it[UserDoc.name] = 123
            // it[UserDoc.groups.list()] = "root"
            it[UserDoc.id] = 0
            it[UserDoc.name] = "root"
            it[UserDoc.groups.list()] = mutableListOf("root", "wheel")
            it[UserDoc.about] = "Super user"
        },
        DynDocSource {
            it[UserDoc.id] = 1
            it[UserDoc.name] = "daemon"
            it[UserDoc.groups.list()] = mutableListOf("daemon")
            it[UserDoc.about] = "Daemon user"
        },
        DynDocSource {
            it[UserDoc.id] = 65535
            it[UserDoc.name] = "nobody"
            it[UserDoc.groups.list()] = mutableListOf("nobody")
            it[UserDoc.about] = "Just nobody"
        },
        DynDocSource {
            it[UserDoc.id] = 65534
            it[UserDoc.name] = "noone"
            it[UserDoc.groups.list()] = mutableListOf("nobody")
            it[UserDoc.about] = "Another nobody"
        },
    )
    // Create index actions, make bulk request and refresh the index
    userIndex.bulk(
        docs.map { doc ->
            IndexAction(
                meta = IdentActionMeta(id = doc[UserDoc.id].toString()),
                source = doc,
            )
        },
        refresh = Refresh.TRUE,
    )
}
