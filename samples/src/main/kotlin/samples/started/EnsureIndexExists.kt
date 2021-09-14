package samples.started

import dev.evo.elasticmagic.Params

suspend fun ensureIndexExists() {
    if (!cluster.indexExists(userIndex.name)) {
        cluster.createIndex(
            userIndex.name,
            mapping = UserDoc,
            settings = Params(
                "index.number_of_replicas" to 0,
            ),
        )
    } else {
        cluster.updateMapping(userIndex.name, mapping = UserDoc)
    }
}
