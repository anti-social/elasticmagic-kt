package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.ElasticsearchIndex
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

expect val esTransport: ElasticsearchKtorTransport
expect val cluster: ElasticsearchCluster
expect val userIndex: ElasticsearchIndex
