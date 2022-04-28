package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.types.IntType

/**
 * [PageFilter] allows to paginate search query results.
 *
 * @param name - a name of the [PageFilter]
 * @param defaultPageSize - default number of hits per page
 * @param availablePageSizes - list of available of per page variants
 * @param maxHits - maximum number of hits to available for a pagination
 */
class PageFilter(
    name: String? = null,
    private val defaultPageSize: Int = 10,
    @Suppress("MagicNumber")
    private val availablePageSizes: List<Int> = listOf(10, 50, 100),
    private val maxHits: Int = 10_000,
) : Filter<PageFilterContext, PageFilterResult>(name) {
    override fun prepareContext(name: String, params: QueryFilterParams): PageFilterContext {
        val page = params.decodeLastValue(name to "", IntType)?.coerceAtLeast(1) ?: 1
        val pageSize = params.decodeLastValue(name to "size", IntType)?.coerceAtLeast(1)
            ?: defaultPageSize
        val perPage = if (availablePageSizes.contains(pageSize)) {
            pageSize
        } else {
            defaultPageSize
        }
        val from = ((page - 1) * perPage).coerceAtMost(maxHits - 1)
        val size = perPage.coerceAtMost(maxHits - from)
        return PageFilterContext(
            name,
            page = page,
            perPage = perPage,
            from = from,
            size = size,
        )
    }

    override fun apply(
        searchQuery: SearchQuery<*>,
        filterCtx: FilterContext,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val ctx = filterCtx.cast<PageFilterContext>()
        searchQuery.trackTotalHits(true).from(ctx.from).size(ctx.size)
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
        filterCtx: FilterContext
    ): PageFilterResult {
        val ctx = filterCtx.cast<PageFilterContext>()
        val totalHits = searchQueryResult.totalHits
            ?: throw IllegalArgumentException("Expected total hits")
        return PageFilterResult(
            ctx.name,
            hits = searchQueryResult.hits,
            totalHits = totalHits,
            page = ctx.page,
            perPage = ctx.perPage,
            totalPages = ((totalHits - 1) / ctx.perPage).toInt() + 1,
            from = ctx.from,
            size = ctx.size,
        )
    }
}

/**
 * Temporal storage for some [PageFilter] parameters.
 */
class PageFilterContext(
    name: String,
    val page: Int,
    val perPage: Int,
    val from: Int,
    val size: Int,
) : FilterContext(name, null)

/**
 * [PageFilterResult] holds result of a [PageFilter].
 *
 * @param name - name of a [PageFilter]
 * @param hits - list of hits on the current page
 * @param totalHits - total number of hits
 * @param page - number of the current page
 * @param perPage - number of hits per page
 * @param totalPages - total number of pages
 * @param from - value used in [SearchQuery.from]
 * @param size - value used in [SearchQuery.size]
 */
data class PageFilterResult(
    override val name: String,
    val hits: List<SearchHit<*>>,
    val totalHits: Long,
    val page: Int,
    val perPage: Int,
    val totalPages: Int,
    val from: Int,
    val size: Int,
) : FilterResult
