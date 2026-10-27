package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.BoundRuntimeField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.ext.elasticsearch.DenseVector
import dev.evo.elasticmagic.ext.elasticsearch.Knn
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryExpressionNode
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.SearchExt
import dev.evo.elasticmagic.query.Source
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.types.BooleanType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private data class SimpleExtension(override val name: String) : SearchExt {
    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
    }

}

@OptIn(ExperimentalTime::class)
class SearchQueryTests {
    @Test
    fun cloning() {
        val userDoc = object : Document() {
            val login by keyword()
            val isActive by boolean()
        }

        val sq1 = SearchQuery()
            .filter(userDoc.login.eq("root"))
            .ext(SimpleExtension("test"))
            .setTimeout(10.seconds)

        val sq2 = sq1.clone()
            .filter(userDoc.isActive.eq(true))
            .size(1)

        sq1.prepareSearch().let {
            it.size shouldBe null
            it.filters.size shouldBe 1
            it.extensions.size shouldBe 1
            it.timeout shouldBe 10.seconds
        }
        sq2.prepareSearch().let {
            it.size shouldBe 1
            it.filters.size shouldBe 2
            it.extensions.size shouldBe 1
        }
    }

    @Test
    fun cloningAllProperties() {
        val hotelDoc = object : Document() {
            val name by keyword()
            val stars by int()
            val location by Field(DenseVector(dims = 2))
        }
        val knn = Knn(hotelDoc.location, listOf(1.1F, 1.2F))
        val runtimeField = BoundRuntimeField(
            "has_name", BooleanType, Script.Source("emit(true)"), hotelDoc
        )

        val sq1 = SearchQuery()
            .source(hotelDoc.name)
            .knn(knn)
            .runtimeMappings(runtimeField)
            .filter(hotelDoc.stars.gte(3))
        sq1.prepareSearch()

        val sq2 = sq1.clone()
        sq2.prepareSearch().let {
            it.source shouldBe Source.Filter(listOf(hotelDoc.name), emptyList())
            it.knn shouldBe knn
            it.runtimeMappings shouldBe mapOf("has_name" to runtimeField)
            it.filters shouldBe listOf(hotelDoc.stars.gte(3))
        }

        // Clones must not affect each other
        sq2.filter(hotelDoc.stars.lte(4)).source(hotelDoc.stars)
        sq1.filter(hotelDoc.stars.eq(5)).runtimeMappings(SearchQuery.CLEAR)

        sq1.prepareSearch().let {
            it.source shouldBe Source.Filter(listOf(hotelDoc.name), emptyList())
            it.runtimeMappings shouldBe emptyMap()
            it.filters shouldBe listOf(hotelDoc.stars.gte(3), hotelDoc.stars.eq(5))
        }
        sq2.prepareSearch().let {
            it.source shouldBe Source.Filter(listOf(hotelDoc.name, hotelDoc.stars), emptyList())
            it.runtimeMappings shouldBe mapOf("has_name" to runtimeField)
            it.filters shouldBe listOf(hotelDoc.stars.gte(3), hotelDoc.stars.lte(4))
        }
    }

    @Test
    fun cloningNodes() {
        val fsHandle = NodeHandle<FunctionScore>()
        val sq1 = SearchQuery(
            QueryExpressionNode(
                fsHandle,
                FunctionScore(functions = emptyList())
            )
        )
        val sq2 = sq1.clone()

        sq1.queryNode(fsHandle) { node ->
            node.copy(
                functions = node.functions + listOf(FunctionScore.RandomScore())
            )
        }

        sq1.prepareSearch().query shouldBe QueryExpressionNode(
            fsHandle,
            FunctionScore(
                functions = listOf(FunctionScore.RandomScore())
            )
        )

        sq2.prepareSearch().query shouldBe QueryExpressionNode(
            fsHandle,
            FunctionScore(functions = emptyList())
        )
    }

    @Test
    fun preparedSearchIsNotAffectedByFurtherModifications() {
        val productDoc = object : Document() {
            val color by keyword()
            val brand by keyword()
            val isActive by boolean()
        }

        val sq = SearchQuery()
            .filter(productDoc.isActive.eq(true))

        // Typical multi search usage: reuse a base query to prepare several searches
        val preparedQueries = listOf("red", "blue").map { color ->
            sq.filter(productDoc.color.eq(color))
                .aggs("brands" to TermsAgg(productDoc.brand))
                .prepareSearch()
        }

        preparedQueries[0].filters shouldBe listOf(
            productDoc.isActive.eq(true),
            productDoc.color.eq("red"),
        )
        preparedQueries[1].filters shouldBe listOf(
            productDoc.isActive.eq(true),
            productDoc.color.eq("red"),
            productDoc.color.eq("blue"),
        )
    }

    @Test
    fun preparedSearchIsSnapshot() {
        val productDoc = object : Document() {
            val price by float()
            val brand by keyword()
        }

        val sq = SearchQuery()
            .size(10)
            .postFilter(productDoc.brand.eq("acme"))
            .sort(productDoc.price.desc())
        val prepared = sq.prepareSearch()
        val preparedCount = sq.prepareCount()

        sq.size(0)
            .postFilter(productDoc.brand.eq("other"))
            .aggs("prices" to TermsAgg(productDoc.price))
            .sort(productDoc.price.asc())

        prepared.size shouldBe 10
        prepared.postFilters shouldBe listOf(productDoc.brand.eq("acme"))
        prepared.aggregations shouldBe emptyMap()
        prepared.sorts shouldBe listOf(productDoc.price.desc())
        preparedCount.postFilters shouldBe listOf(productDoc.brand.eq("acme"))

        // The builder itself must keep all the modifications
        sq.prepareSearch().let {
            it.size shouldBe 0
            it.postFilters shouldBe listOf(
                productDoc.brand.eq("acme"),
                productDoc.brand.eq("other"),
            )
            it.aggregations.keys shouldBe setOf("prices")
            it.sorts shouldBe listOf(productDoc.price.desc(), productDoc.price.asc())
        }
    }
}
