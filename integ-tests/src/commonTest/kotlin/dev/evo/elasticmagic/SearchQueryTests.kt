package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.CalendarInterval
import dev.evo.elasticmagic.aggs.DateHistogramAgg
import dev.evo.elasticmagic.aggs.DateHistogramAggResult
import dev.evo.elasticmagic.aggs.HistogramAgg
import dev.evo.elasticmagic.aggs.HistogramAggResult
import dev.evo.elasticmagic.aggs.NestedAgg
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.DocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.withActionMeta
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.doc.enum
import dev.evo.elasticmagic.doc.instant
import dev.evo.elasticmagic.query.Ids
import dev.evo.elasticmagic.query.Nested
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.match
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.types.KeywordType

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.string.shouldHaveMinLength
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

import kotlin.test.Test

enum class OrderStatus(val id: Int) : ToValue<Int> {
    NEW(0), ACCEPTED(1), SHIPPED(2), DELIVERED(3), CANCELLED(4);

    override fun toValue() = id
}

object OrderDoc : Document() {
    class User(field: BoundField<BaseDocSource, Nothing>) : SubDocument(field) {
        val id by int()
        val name by text()
        val phone by keyword()
        val rating by float()
    }

    class CartItem(field: BoundField<BaseDocSource, Nothing>) : SubDocument(field) {
        val productId by long("product_id")
        val productName by text("product_name")
        val productPrice by float("product_price")
        val quantity by int()
    }

    val user by obj(::User)
    val items by nested(::CartItem)
    val status by int().enum(OrderStatus::id)
    val comment by text()
    val dateCreated by instant()
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
    var dateCreated by OrderDoc.dateCreated.required()
}

class SearchQueryTests : ElasticsearchTestBase() {
    override val indexName = "search-query"

    private val docSources = mutableMapOf<String, DocSourceAndMeta<*>>()

    private val karlsson = OrderDocSource.User().apply {
        id = 1
        name = "Karlson"
        phone = "223-322"
    }
    private val karlssonsJam = OrderDocSource().apply {
        user = karlsson
        items = mutableListOf(
            OrderDocSource.CartItem().apply {
                productId = 1
                productName = "Jam"
                productPrice = 5.0F
                quantity = 3
            }
        )
        status = OrderStatus.NEW
        comment = "Call me later"
        dateCreated = LocalDateTime(2019, 4, 30, 12, 29, 35).toInstant(TimeZone.UTC)
    }
        .withActionMeta(id = "101")
        .also { docSources[it.meta.id] = it }
    private val karlssonsBestDonuts = OrderDocSource().apply {
        user = karlsson
        items = mutableListOf(
            OrderDocSource.CartItem().apply {
                productId = 2
                productName = "Genuine Miss Bock's donut"
                productPrice = 0.2F
                quantity = 100
            }
        )
        status = OrderStatus.NEW
        dateCreated = LocalDateTime(2020, 12, 23, 8, 47, 8).toInstant(TimeZone.UTC)
    }
        .withActionMeta(id = "102")
        .also { docSources[it.meta.id] = it }
    private val karlssonsJustDonuts = OrderDocSource().apply {
        user = karlsson
        items = mutableListOf(
            OrderDocSource.CartItem().apply {
                productId = 3
                productName = "Shop donut"
                productPrice = 0.1F
                quantity = 10
            }
        )
        status = OrderStatus.ACCEPTED
        dateCreated = LocalDateTime(2020, 12, 31, 23, 59, 59).toInstant(TimeZone.UTC)
    }
        .withActionMeta(id = "103")
        .also { docSources[it.meta.id] = it }
    private val karlssonsJustAnotherDonuts = OrderDocSource().apply {
        user = karlsson
        items = mutableListOf(
            OrderDocSource.CartItem().apply {
                productId = 3
                productName = "Shop donut"
                productPrice = 0.1F
                quantity = 10
            }
        )
        status = OrderStatus.ACCEPTED
        dateCreated = LocalDateTime(2020, 12, 31, 0, 0, 0).toInstant(TimeZone.UTC)
    }
        .withActionMeta(id = "104")
        .also { docSources[it.meta.id] = it }
    private val littleBrother = OrderDocSource.User().apply {
        id = 2
        name = "Svante"
        phone = "123-321"
    }
    private val littleBrotherDogStuff = OrderDocSource().apply {
        user = littleBrother
        items = mutableListOf(
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
            OrderDocSource.CartItem().apply {
                productId = 2
                productName = "Genuine Miss Bock's donut"
                productPrice = 0.2F
                quantity = 2
            }
        )
        status = OrderStatus.NEW
        dateCreated = LocalDateTime(2021, 10, 9, 15, 31, 45).toInstant(TimeZone.UTC)
    }
        .withActionMeta(id = "105")
        .also { docSources[it.meta.id] = it }

    private fun TestScope.checkOrderHits(hits: List<SearchHit<OrderDocSource>>, expectedIds: Set<String>) {
        hits.size shouldBe expectedIds.size
        for (hit in hits) {
            hit.index shouldBe index.name
            hit.type shouldBe "_doc"
            hit.version shouldBe null
            hit.seqNo shouldBe null
            hit.primaryTerm shouldBe null
            val doc = hit.source.shouldNotBeNull()
            doc shouldBe docSources[hit.id]?.doc
        }
    }

    private fun TestScope.checkOrderHitsSorted(hits: List<SearchHit<OrderDocSource>>, expectedIds: List<String>) {
        hits.size shouldBe expectedIds.size
        for ((hit, orderId) in hits.zip(expectedIds)) {
            hit.index shouldBe index.name
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
    fun badRequest() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsBestDonuts)) {
            shouldThrow<ElasticsearchException.BadRequest> {
                SearchQuery(::OrderDocSource)
                    .sort(
                        Sort(
                            Script.Source("doc['_id'].value"),
                            scriptType = "unknown",
                        )
                    )
                    .execute(index)
            }
        }

        withFixtures(OrderDoc, listOf(karlssonsBestDonuts)) {
            val query = SearchQuery(::OrderDocSource)
                .sort(
                    Sort(
                        Script.Source("doc['unknown'].value"),
                        scriptType = "number"
                    )
                )
            when (val version = cluster.getVersion()) {
                is Version.Opensearch -> {
                    shouldThrow<ElasticsearchException.BadRequest> {
                        query.execute(index)
                    }
                }
                is Version.Elasticsearch -> {
                    if (version.major < 7) {
                        shouldThrow<ElasticsearchException.Internal> {
                            query.execute(index)
                        }
                    } else {
                        shouldThrow<ElasticsearchException.BadRequest> {
                            query.execute(index)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun emptyQuery() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
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
    fun source() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsJam)) {
            val searchResult = SearchQuery(::OrderDocSource)
                .source(excludes=listOf(OrderDoc.comment))
                .execute(index)

            searchResult.totalHits shouldBe 1
            searchResult.maxScore shouldBe 1.0F

            searchResult.hits[0].shouldNotBeNull().let { hit ->
                val doc = hit.source.shouldNotBeNull()
                doc.status shouldBe OrderStatus.NEW
                doc.dateCreated shouldBe LocalDateTime(2019, 4, 30, 12, 29, 35).toInstant(TimeZone.UTC)
                doc.comment.shouldBeNull()
            }
        }
    }

    @Test
    fun source_disabled() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsJam)) {
            val searchResult = SearchQuery(::OrderDocSource)
                .source(false)
                .execute(index)

            searchResult.totalHits shouldBe 1
            searchResult.maxScore shouldBe 1.0F

            searchResult.hits[0].shouldNotBeNull().let { hit ->
                hit.source.shouldBeNull()
            }
        }
    }

    @Test
    fun source_missingRequiredField() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsJam)) {
            val exc = shouldThrow<IllegalStateException> {
                SearchQuery(::OrderDocSource)
                    .source(OrderDoc.comment)
                    .execute(index)
            }
            exc.message shouldBe "Field [user] is required"
        }
    }

    @Test
    fun docvalueFields() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsJam)) {
            val searchResult = SearchQuery(::OrderDocSource)
                .docvalueFields(OrderDoc.status, OrderDoc.dateCreated.format("YYYY"))
                .execute(index)

            searchResult.totalHits shouldBe 1
            searchResult.maxScore shouldBe 1.0F

            searchResult.hits[0].shouldNotBeNull().let { hit ->
                val fields = hit.fields
                fields[OrderDoc.status] shouldBe listOf(OrderStatus.NEW)
                fields["status"] shouldBe listOf(0L)
                fields[OrderDoc.dateCreated] shouldBe listOf(LocalDateTime(2019, 1, 1, 0, 0).toInstant(TimeZone.UTC))
                fields["dateCreated"] shouldBe listOf("2019")
            }
        }
    }

    @Test
    fun simpleTermQuery() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsJam, littleBrotherDogStuff)) {
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
    fun simpleNestedQuery() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts)) {
            val searchResult = index.search(
                SearchQuery(
                    ::OrderDocSource,
                    Nested(
                        OrderDoc.items,
                        OrderDoc.items.productName.match("donut")
                    )
                ).prepareSearch()
            )

            searchResult.totalHits shouldBe 2
            searchResult.maxScore.shouldNotBeNull() shouldBeGreaterThan 0.0F

            checkOrderHits(searchResult.hits, setOf("103", "102"))
        }
    }

    @Test
    fun simpleFiltering() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .filter(OrderDoc.status.eq(OrderStatus.NEW))
                .execute(index)

            searchResult.totalHits shouldBe 3
            searchResult.maxScore.shouldNotBeNull() shouldBe 0.0F

            checkOrderHits(searchResult.hits, setOf("101", "102", "104"))
        }
    }

    @Test
    fun rangeDateFiltering() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .filter(OrderDoc.dateCreated.lt(LocalDate(2020, 1, 1).atStartOfDayIn(TimeZone.UTC)))
                .execute(index)

            searchResult.totalHits shouldBe 1
            searchResult.maxScore.shouldNotBeNull() shouldBe 0.0F

            checkOrderHits(searchResult.hits, setOf("101"))
            val hit = searchResult.hits[0]
            val order = hit.source.shouldNotBeNull()
            order.status shouldBe OrderStatus.NEW
            order.user.name shouldBe "Karlson"
            order.items.size shouldBe 1
            order.dateCreated shouldBe LocalDateTime(2019, 4, 30, 12, 29, 35).toInstant(TimeZone.UTC)
        }
    }

    @Test
    fun idsFiltering() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .filter(Ids(listOf("105", "102")))
                .execute(index)

            searchResult.totalHits shouldBe 2
            searchResult.maxScore shouldBe 0.0F

            checkOrderHits(searchResult.hits, setOf("102", "105"))
        }
    }

    @Test
    fun sortScript() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .sort(
                    Sort(
                        Script.Source(
                            "doc[params.date_field].value.toInstant().toEpochMilli()",
                            params = Params(
                                "date_field" to OrderDoc.dateCreated
                            )
                        ),
                        scriptType = "number",
                        order = Sort.Order.DESC,
                    )
                )
                .execute(index)

            searchResult.totalHits shouldBe 4
            searchResult.maxScore shouldBe null

            val hits = searchResult.hits
            checkOrderHitsSorted(hits, listOf("105", "103", "102", "101"))
            hits[0].sort shouldBe listOf(1.633793505E12)
            hits[1].sort shouldBe listOf(1.609459199E12)
            hits[2].sort shouldBe listOf(1.608713228E12)
            hits[3].sort shouldBe listOf(1.556627375E12)
        }
    }

    @Test
    fun sortNested() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .sort(
                    Sort(
                        OrderDoc.items.quantity,
                        order = Sort.Order.DESC,
                        mode = Sort.Mode.SUM,
                        nested = Sort.Nested(OrderDoc.items)
                    )
                )
                .execute(index)

            searchResult.totalHits shouldBe 4
            searchResult.maxScore shouldBe null

            val hits = searchResult.hits
            checkOrderHitsSorted(hits, listOf("102", "105", "103", "101"))
            hits[0].sort shouldBe listOf(100)
            hits[1].sort shouldBe listOf(13)
            hits[2].sort shouldBe listOf(10)
            hits[3].sort shouldBe listOf(3)
        }
    }

    @Test
    fun aggTerms() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
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
            // Elasticsearch 6.x has max score 0.0, but 7.x has null
            (searchResult.maxScore ?: 0.0) shouldBe 0.0
            searchResult.hits.size shouldBe 0

            val statusesAgg = searchResult.agg<TermsAggResult<OrderStatus>>("statuses")
            statusesAgg.buckets.size shouldBe 2
            statusesAgg.buckets[0].key shouldBe OrderStatus.NEW
            statusesAgg.buckets[0].docCount shouldBe 3
            statusesAgg.buckets[1].key shouldBe OrderStatus.ACCEPTED
            statusesAgg.buckets[1].docCount shouldBe 1
        }
    }

    @Test
    fun agg_dateHistogram() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val version = index.cluster.getVersion()
            val interval = if (
                version is Version.Opensearch ||
                version is Version.Elasticsearch && version.major >= 7
            ) {
                DateHistogramAgg.Interval.Calendar(CalendarInterval.YEAR)
            } else {
                DateHistogramAgg.Interval.Legacy("1y")
            }
            val searchResult = SearchQuery(::OrderDocSource)
                .aggs(
                    "orders_by_year" to DateHistogramAgg(
                        OrderDoc.dateCreated,
                        interval = interval,
                    ),
                )
                .size(0)
                .execute(index)

            val ordersByYear = searchResult.agg<DateHistogramAggResult<Instant>>("orders_by_year")
            ordersByYear.buckets.shouldHaveSize(3)
            ordersByYear.buckets[0].key shouldBe 1546300800000L
            ordersByYear.buckets[0].keyAsString shouldBe "2019-01-01T00:00:00.000Z"
            ordersByYear.buckets[0].keyAsDatetime shouldBe LocalDate(2019, 1, 1).atStartOfDayIn(TimeZone.UTC)
            ordersByYear.buckets[0].docCount shouldBe 1
            ordersByYear.buckets[1].key shouldBe 1577836800000L
            ordersByYear.buckets[1].keyAsString shouldBe "2020-01-01T00:00:00.000Z"
            ordersByYear.buckets[1].keyAsDatetime shouldBe LocalDate(2020, 1, 1).atStartOfDayIn(TimeZone.UTC)
            ordersByYear.buckets[1].docCount shouldBe 2
            ordersByYear.buckets[2].key shouldBe 1609459200000L
            ordersByYear.buckets[2].keyAsString shouldBe "2021-01-01T00:00:00.000Z"
            ordersByYear.buckets[2].keyAsDatetime shouldBe LocalDate(2021, 1, 1).atStartOfDayIn(TimeZone.UTC)
            ordersByYear.buckets[2].docCount shouldBe 1
        }
    }

    @Test
    fun aggNested() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val searchResult = SearchQuery(::OrderDocSource)
                .aggs(
                    "cart_items" to NestedAgg(
                        OrderDoc.items,
                        aggs = mapOf(
                            "item_price_hist" to HistogramAgg(
                                OrderDoc.items.productPrice,
                                interval = 1.0F,
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
            cartItemsAgg.docCount shouldBe 6
            val priceHistAgg = cartItemsAgg.agg<HistogramAggResult>("item_price_hist")
            priceHistAgg.buckets.size shouldBe 4
            priceHistAgg.buckets[0].key shouldBe 0.0
            priceHistAgg.buckets[0].docCount shouldBe 3
            priceHistAgg.buckets[1].key shouldBe 1.0
            priceHistAgg.buckets[1].docCount shouldBe 1
            priceHistAgg.buckets[2].key shouldBe 5.0
            priceHistAgg.buckets[2].docCount shouldBe 1
            priceHistAgg.buckets[3].key shouldBe 10.0
            priceHistAgg.buckets[3].docCount shouldBe 1
        }
    }

    @Test
    fun runtimeMappings() = runTestWithSerdes {
        withFixtures(
            OrderDoc,
            listOf(karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, karlssonsJustAnotherDonuts)
        ) {
            val version = cluster.getVersion()
            if (
                version is Version.Opensearch ||
                version is Version.Elasticsearch && version < Version.Elasticsearch(7, 11, 0)
            ) {
                return@withFixtures
            }

            val statusStrField = OrderDoc.runtimeField(
                "status_str",
                KeywordType,
                Script.Source("emit(doc['status'].value.toString())")
            )
            val dayOfWeekField = OrderDoc.runtimeField(
                "day_of_week",
                KeywordType,
                Script.Source(
                    """
                    emit(
                        doc[params.timestampField].value
                            .dayOfWeekEnum
                            .getDisplayName(TextStyle.FULL, Locale.ROOT)
                    )""".trimIndent(),
                    params = Params(
                        "timestampField" to OrderDoc.dateCreated
                    )
                )
            )
            val searchResult = SearchQuery()
                .runtimeMappings(dayOfWeekField, statusStrField)
                .aggs("days_of_week" to TermsAgg(dayOfWeekField))
                .fields(dayOfWeekField, statusStrField)
                .sort(dayOfWeekField)
                .execute(index)

            searchResult.hits.size shouldBe 4
            searchResult.hits.flatMap { hit -> hit.fields[dayOfWeekField]!! } shouldBe listOf(
                "Thursday", "Thursday", "Tuesday", "Wednesday"
            )
            searchResult.hits.flatMap { hit -> hit.fields[statusStrField]!! } shouldBe listOf(
                "1", "1", "0", "0"
            )
            val daysOfWeekAgg = searchResult.agg<TermsAggResult<String>>("days_of_week")
            daysOfWeekAgg.buckets.size shouldBe 3
            daysOfWeekAgg.buckets[0].key shouldBe "Thursday"
            daysOfWeekAgg.buckets[0].docCount shouldBe 2
            daysOfWeekAgg.buckets[1].key shouldBe "Tuesday"
            daysOfWeekAgg.buckets[1].docCount shouldBe 1
            daysOfWeekAgg.buckets[2].key shouldBe "Wednesday"
            daysOfWeekAgg.buckets[2].docCount shouldBe 1
        }
    }

    @Test
    fun multiSearch() = runTestWithSerdes {
        withFixtures(
            OrderDoc,
            listOf(
                karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
            ),
        ) {
            val newOrdersQuery = SearchQuery(::OrderDocSource)
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .sort(OrderDoc.dateCreated)
            val acceptedOrdersQuery = SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.ACCEPTED)
                .sort(OrderDoc.dateCreated)
            val searchResult = index.multiSearch(listOf(
                newOrdersQuery.prepareSearch(), acceptedOrdersQuery.prepareSearch()
            ))

            searchResult.responses.size shouldBe 2

            val newOrdersResult = searchResult.responses[0]
            newOrdersResult.totalHits shouldBe 3
            newOrdersResult.hits[0].source.shouldBeInstanceOf<OrderDocSource>()
            newOrdersResult.hits[0].id shouldBe "101"
            newOrdersResult.hits[1].id shouldBe "102"
            newOrdersResult.hits[2].id shouldBe "105"

            val acceptedOrdersResult = searchResult.responses[1]
            acceptedOrdersResult.totalHits shouldBe 1
            acceptedOrdersResult.hits[0].source.shouldBeInstanceOf<DynDocSource>()
            acceptedOrdersResult.hits[0].id shouldBe "103"
        }
    }

    @Test
    fun count() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            SearchQuery()
                .count(index).let { res ->
                    res.count shouldBe 4
                }

            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .docvalueFields(OrderDoc.status)
                .count(index).let { res ->
                    res.count shouldBe 3
                }
        }
    }

    @Test
    fun updateByQuery() = runTestWithSerdes() {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.ACCEPTED)
                .count(index).let { res ->
                    res.count shouldBe 1
                }

            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .update(
                    index,
                    Script.Source(
                        "ctx._source.status = params.new_status",
                        params = Params(
                            "new_status" to OrderStatus.ACCEPTED
                        )
                    ),
                    refresh = Refresh.TRUE
                ).let { res ->
                    res.updated shouldBe 3
                }

            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .count(index).let { res ->
                    res.count shouldBe 0
                }

            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.ACCEPTED)
                .count(index).let { res ->
                    res.count shouldBe 4
                }
        }
    }

    @Test
    fun updateByQuery_conflict() = runTestWithSerdes() {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.ACCEPTED)
                .count(index).let { res ->
                    res.count shouldBe 1
                }

            shouldThrow<ElasticsearchException.BadRequest> {
                SearchQuery()
                    .filter(OrderDoc.status eq OrderStatus.NEW)
                    .update(
                        index,
                        Script.Source(
                            "ctx._source.status = 'accepted'",
                        ),
                        refresh = Refresh.TRUE
                    )
            }
        }
    }

    @Test
    fun updateByQueryAsync() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.ACCEPTED)
                .count(index).let { res ->
                    res.count shouldBe 1
                }

            val result = SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .updateAsync(
                    index,
                    Script.Source(
                        "ctx._source.status = params.new_status",
                        params = Params(
                            "new_status" to OrderStatus.ACCEPTED
                        )
                    ),
                    refresh = Refresh.TRUE
                )

            result.task shouldHaveMinLength 1
            // TODO: Implement task API and check task result
        }
    }

    @Test
    fun deleteByQuery() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .docvalueFields(OrderDoc.status)
                .delete(index, refresh = Refresh.TRUE).let { res ->
                    res.deleted shouldBe 3
                }

            SearchQuery()
                .count(index).let { res ->
                    res.count shouldBe 1
                }
        }
    }

    @Test
    fun deleteByQueryAsync() = runTestWithSerdes {
        withFixtures(OrderDoc, listOf(
            karlssonsJam, karlssonsBestDonuts, karlssonsJustDonuts, littleBrotherDogStuff
        )) {
            val result = SearchQuery()
                .filter(OrderDoc.status eq OrderStatus.NEW)
                .docvalueFields(OrderDoc.status)
                .deleteAsync(index, refresh = Refresh.TRUE)

            result.task shouldHaveMinLength 1
            // TODO: Implement task API and check task result
        }
    }
}
