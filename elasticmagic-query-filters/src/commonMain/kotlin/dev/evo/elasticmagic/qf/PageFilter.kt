package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.types.IntType

/**
 * [PageFilter] allows to paginate search query results.
 *
 * @param name - name of the [PageFilter]
 * @param availablePageSizes - list of available of per page variants
 * @param defaultPageSize - default number of hits per page
 * @param maxHits - maximum number of hits to available for a pagination
 */
class PageFilter(
    name: String? = null,
    val availablePageSizes: List<Int> = DEFAULT_AVAILABLE_PAGE_SIZES,
    defaultPageSize: Int? = null,
    val maxHits: Int = DEFAULT_MAX_HITS,
) : Filter<PreparedPageFilter, PageFilterResult>(name) {
    init {
        if (availablePageSizes.isEmpty()) {
            throw IllegalArgumentException("availablePageSizes argument should not be empty")
        }
    }

    val defaultPageSize = defaultPageSize ?: availablePageSizes[0]

    companion object {
        const val DEFAULT_MAX_HITS = 10_000
        val DEFAULT_AVAILABLE_PAGE_SIZES = listOf(10, 50, 100)
    }

    /**
     * Parses [params] and prepares the [PageFilter] for applying.
     *
     * @param name - name of the filter
     * @param params - parameters that should be applied to a search query.
     *   Supports 2 operations:
     *   - `""` specifies current page
     *   - `"size"` sets number of documents on a page
     *   Examples:
     *   - `mapOf(listOf("page") to listOf("2"), listOf("page", "size") to listOf("100"))`
     */
    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedPageFilter {
        val page = params.decodeLastValue(paramName, IntType)?.coerceAtLeast(1) ?: 1
        val pageSize = params.decodeLastValue(listOf(paramName, "size"), IntType)?.coerceAtLeast(1)
            ?: defaultPageSize
        val perPage = if (availablePageSizes.contains(pageSize)) {
            pageSize
        } else {
            defaultPageSize
        }
        val from = ((page - 1) * perPage).coerceAtMost(maxHits)
        val size = perPage.coerceAtMost(maxHits - from)
        return PreparedPageFilter(
            this,
            name,
            paramName,
            page = page,
            perPage = perPage,
            from = from,
            size = size,
        )
    }
}

/**
 * Filter that is ready for applying to a search query.
 */
class PreparedPageFilter(
    val filter: PageFilter,
    name: String,
    paramName: String,
    val page: Int,
    val perPage: Int,
    val from: Int,
    val size: Int,
) : PreparedFilter<PageFilterResult>(name, paramName, null) {
    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        searchQuery.trackTotalHits(true).from(from).size(size)
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
    ): PageFilterResult {
        val totalHits = searchQueryResult.totalHits
            ?: throw IllegalArgumentException("Expected total hits")
        val maxTotalPages = (filter.maxHits - 1) / perPage + 1
        return PageFilterResult(
            name,
            paramName,
            hits = searchQueryResult.hits,
            totalHits = totalHits,
            page = page,
            perPage = perPage,
            totalPages = (((totalHits - 1) / perPage).toInt() + 1).coerceAtMost(maxTotalPages),
            from = from,
            size = size,
        )
    }
}

/**
 * [PageFilterResult] holds result of a [PageFilter].
 *
 * @param name - name of a [PageFilter]
 * @param hits - list of hits on the current page
 * @param totalHits - total number of hits
 * @param page - number of the current page
 * @param perPage - number of hits per page
 * @param totalPages - total number of pages
 * @param from - value applied to [SearchQuery.from]
 * @param size - value applied to [SearchQuery.size]
 */
data class PageFilterResult(
    override val name: String,
    override val paramName: String,
    val hits: List<SearchHit<*>>,
    val totalHits: Long,
    val page: Int,
    val perPage: Int,
    val totalPages: Int,
    val from: Int,
    val size: Int,
) : FilterResult, Iterable<SearchHit<*>> {
    override fun iterator(): Iterator<SearchHit<*>> {
        return hits.iterator()
    }
}
