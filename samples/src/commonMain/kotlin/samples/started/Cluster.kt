package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

const val DEFAULT_ELASTIC_URL = "http://localhost:9200"
const val DEFAULT_ELASTIC_USER = "elastic"

expect val esTransport: ElasticsearchKtorTransport
val cluster = ElasticsearchCluster(esTransport)
val userIndex = cluster["elasticmagic-samples_user"]
