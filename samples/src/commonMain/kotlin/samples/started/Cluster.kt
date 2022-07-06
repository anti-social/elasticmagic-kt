package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

expect val esTransport: ElasticsearchKtorTransport
val cluster = ElasticsearchCluster(esTransport)
val userIndex = cluster["user"]
