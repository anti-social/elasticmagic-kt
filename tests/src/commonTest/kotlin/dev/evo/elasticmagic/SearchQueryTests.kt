package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.ElasticsearchException

import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull

import io.kotest.matchers.shouldBe

import kotlin.test.Test

object OrderDoc : Document() {
    class User : SubDocument() {
        val id by int()
        val name by text()
        val phone by keyword()
        val rating by float()
    }

    class CartItem : SubDocument() {
        val productId by long("product_id")
        val productName by text("product_name")
        val productPrice by float("product_price")
        val quantity by int()
    }

    val user by obj(::User)
    val items by nested(::CartItem)
    val status by int()
    val comment by text()
}

class OrderDocSource : DocSource() {
    class User : DocSource() {
        var id by OrderDoc.user.id.required()
        var name by OrderDoc.user.name.required()
        var phone by OrderDoc.user.phone.required()
        var rating by OrderDoc.user.rating
    }

    class CartItem : DocSource() {
        var productId by OrderDoc.items.productId.required()
        var productName by OrderDoc.items.productName.required()
        var productPrice by OrderDoc.items.productPrice.required()
        var quantity by OrderDoc.items.quantity.required()
    }

    var user by OrderDoc.user.source(::User).required()
    var items by OrderDoc.items.source(::CartItem).required().list().required()
    var status by OrderDoc.status.required()
    var comment by OrderDoc.comment
}

class SearchQueryTests : ElasticsearchTestBase("test-search-query") {

    private val docSources = mutableMapOf<String, IdentDocSourceWithMeta>()

    private val karlsson = OrderDocSource.User().apply {
        id = 1
        name = "Karlson"
        phone = "223-322"
    }
    private val karlssonsJam = OrderDocSource().apply {
        user = karlsson
        items = listOf(
            OrderDocSource.CartItem().apply {
                productId = 1
                productName = "Jam"
                productPrice = 5.0F
                quantity = 3
            }
        )
        status = 0
    }
        .withActionMeta(id = "101")
        .also { docSources[it.meta.id] = it }
    private val karlssonsBestDonuts = OrderDocSource().apply {
        user = karlsson
        items = listOf(
            OrderDocSource.CartItem().apply {
                productId = 2
                productName = "Genuine Miss Bock's donut"
                productPrice = 0.2F
                quantity = 100
            }
        )
        status = 0
    }
        .withActionMeta(id = "102")
        .also { docSources[it.meta.id] = it }
    private val karlssonsJustDonuts = OrderDocSource().apply {
        user = karlsson
        items = listOf(
            OrderDocSource.CartItem().apply {
                productId = 3
                productName = "Shop donut"
                productPrice = 0.1F
                quantity = 10
            }
        )
        status = 1
    }
        .withActionMeta(id = "103")
        .also { docSources[it.meta.id] = it }
    private val littleBrother = OrderDocSource.User().apply {
        id = 2
        name = "Svante"
        phone = "123-321"
    }
    private val littleBrotherDogStuff = OrderDocSource().apply {
        user = littleBrother
        items = listOf(
            OrderDocSource.CartItem().apply {
                productId = 4
                productName = "Collar"
                productPrice = 10F
                quantity = 2
            },
            OrderDocSource.CartItem().apply {
                productId = 5
                productName = "Dog food"
                productPrice = 1.99F
                quantity = 9
            },
        )
        status = 0
    }
        .withActionMeta(id = "104")
        .also { docSources[it.meta.id] = it }

    private suspend fun ensureIndex() {
        if (!cluster.indexExists(index.indexName)) {
            cluster.createIndex(
                index.indexName,
                mapping = OrderDoc,
                settings = Params(
                    "index.number_of_replicas" to 0,
                ),
            )
        } else {
            cluster.updateMapping(index.indexName, mapping = OrderDoc)
        }
    }

    private suspend fun withFixtures(docs: List<DocSourceAndMeta>, block: suspend () -> Unit) {
        ensureIndex()

        val indexActions = docs.map { docAndMeta ->
            IndexAction(
                docAndMeta.meta,
                docAndMeta.doc,
            )
        }
        val bulkResult = index.bulk(indexActions, refresh = Refresh.TRUE)
        val deleteActions = mutableListOf<Action<*>>()
        val failedItems = mutableListOf<BulkError>()
        for (bulkItem in bulkResult.items) {
            when (bulkItem) {
                is BulkItem.Ok -> {
                    deleteActions.add(DeleteAction(bulkItem))
                }
                is BulkItem.Error -> {
                    failedItems.add(bulkItem.error)
                }
            }
        }
        if (failedItems.isNotEmpty()) {
            throw ElasticsearchException(
                failedItems.joinToString("\n") { bulkErr ->
                    "${bulkErr.type} - ${bulkErr.reason}"
                }
            )
        }
        try {
            block()
        } finally {
            index.bulk(deleteActions, refresh = Refresh.TRUE)
        }
    }

    private fun checkOrderHits(hits: List<SearchHit<OrderDocSource>>, expectedIds: Set<String>) {
        hits.size shouldBe expectedIds.size
        for (hit in hits) {
            val orderId = hit.id
            hit.index shouldBe index.indexName
            hit.type shouldBe "_doc"
            hit.version shouldBe null
            hit.seqNo shouldBe null
            hit.primaryTerm shouldBe null
            val doc = hit.source.shouldNotBeNull()
            doc shouldBe docSources[hit.id]?.doc
        }
    }

    private fun checkOrderHitsSorted(hits: List<SearchHit<OrderDocSource>>, expectedIds: List<String>) {
        hits.size shouldBe expectedIds.size
        for ((hit, orderId) in hits.zip(expectedIds)) {
            hit.index shouldBe index.indexName
            hit.type shouldBe "_doc"
            hit.id shouldBe orderId
            hit.version shouldBe null
            hit.seqNo shouldBe null
            hit.primaryTerm shouldBe null
            val doc = hit.source.shouldNotBeNull()
            doc shouldBe docSources[hit.id]?.doc
        }
    }

    @Test
    fun emptyQuery() = runTest {
        withFixtures(listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .execute(index)

            searchResult.totalHits shouldBe 4
            searchResult.maxScore shouldBe 1.0F

            checkOrderHits(searchResult.hits, setOf("101", "102", "103", "104"))
        }
    }

    @Test
    fun simpleTermQuery() = runTest {
        withFixtures(listOf(karlssonsJam, littleBrotherDogStuff)) {
            val searchResult = SearchQuery(
                ::OrderDocSource,
                OrderDoc.user.id.eq(1)
            )
                .execute(index)

            searchResult.totalHits shouldBe 1
            searchResult.maxScore shouldBe 1.0F

            checkOrderHits(searchResult.hits, setOf("101"))
        }
    }

    @Test
    fun simpleNestedQuery() = runTest {
        withFixtures(listOf(karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts)) {
            val searchResult = index.search(
                SearchQuery(
                    ::OrderDocSource,
                    Nested(
                        OrderDoc.items,
                        OrderDoc.items.productName.match("donut")
                    )
                )
            )

            searchResult.totalHits shouldBe 2
            searchResult.maxScore.shouldNotBeNull() shouldBeLessThan 1.0

            checkOrderHits(searchResult.hits, setOf("103", "102"))
        }
    }

    @Test
    fun simpleFiltering() = runTest {
        withFixtures(listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .filter(OrderDoc.status.eq(0))
                .execute(index)

            searchResult.totalHits shouldBe 3
            searchResult.maxScore.shouldNotBeNull() shouldBeLessThan 1.0

            checkOrderHits(searchResult.hits, setOf("101", "102", "104"))
        }
    }

    @Test
    fun sortNested() = runTest {
        withFixtures(listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .sort(Sort(
                    OrderDoc.items.quantity,
                    order = Sort.Order.DESC,
                    mode = Sort.Mode.SUM,
                    nested = Sort.Nested(OrderDoc.items)
                ))
                .execute(index)

            searchResult.totalHits shouldBe 4
            searchResult.maxScore shouldBe null

            val hits = searchResult.hits
            checkOrderHitsSorted(hits, listOf("102", "104", "103", "101"))
            hits[0].sort shouldBe listOf(100)
            hits[1].sort shouldBe listOf(11)
            hits[2].sort shouldBe listOf(10)
            hits[3].sort shouldBe listOf(3)
        }
    }

    @Test
    fun aggTerms() = runTest {
        withFixtures(listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .aggs(
                    "statuses" to TermsAgg(
                        OrderDoc.status
                    ),
                )
                .size(0)
                .execute(index)

            searchResult.totalHits shouldBe 4
            searchResult.maxScore ?: 0.0 shouldBe 0.0
            searchResult.hits.size shouldBe 0

            val statusesAgg = searchResult.agg<TermsAggResult>("statuses")
            statusesAgg.buckets.size shouldBe 2
            statusesAgg.buckets[0].key shouldBe 0
            statusesAgg.buckets[0].docCount shouldBe 3
            statusesAgg.buckets[1].key shouldBe 1
            statusesAgg.buckets[1].docCount shouldBe 1
        }
    }

    @Test
    fun aggNested() = runTest {
        withFixtures(listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .aggs(
                    "cart_items" to NestedAgg(
                        OrderDoc.items,
                        aggs = mapOf(
                            "item_price_hist" to HistogramAgg(
                                OrderDoc.items.productPrice,
                                interval = 1.0,
                                minDocCount = 1,
                            )
                        )
                    )
                )
                .size(0)
                .execute(index)

            searchResult.totalHits shouldBe 4
            searchResult.maxScore ?: 0.0 shouldBe 0.0
            searchResult.hits.size shouldBe 0

            val cartItemsAgg = searchResult.agg<SingleBucketAggResult>("cart_items")
            cartItemsAgg.docCount shouldBe 5
            val priceHistAgg = cartItemsAgg.agg<HistogramAggResult>("item_price_hist")
            priceHistAgg.buckets.size shouldBe 4
            priceHistAgg.buckets[0].key shouldBe 0.0
            priceHistAgg.buckets[0].docCount shouldBe 2
            priceHistAgg.buckets[1].key shouldBe 1.0
            priceHistAgg.buckets[1].docCount shouldBe 1
            priceHistAgg.buckets[2].key shouldBe 5.0
            priceHistAgg.buckets[2].docCount shouldBe 1
            priceHistAgg.buckets[3].key shouldBe 10.0
            priceHistAgg.buckets[3].docCount shouldBe 1
        }
    }
}
