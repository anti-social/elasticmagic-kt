package samples.bikeshop

import dev.evo.elasticmagic.qf.FacetFilterResult
import dev.evo.elasticmagic.qf.PageFilterResult
import dev.evo.elasticmagic.qf.QueryFiltersResult
import dev.evo.elasticmagic.qf.RangeFacetFilterResult
import dev.evo.elasticmagic.qf.SortFilterResult

class Column(val width: Int, val padding: Int = 1) {
    private val rows = mutableListOf<String>()

    fun hr(symbol: Char = '-') {
        rows.add(symbol.toString().padEnd(width, symbol))
    }

    fun println() = rows.add("".padEnd(width))

    fun println(row: String) {
        if (row.length < width) {
            rows.add(row.padEnd(width))
        } else if (row.length > width) {
            rows.add(row.substring(0, width))
        } else {
            rows.add(row)
        }
    }

    fun sideBySide(right: Column, divider: String = " | "): Column {
        val result = Column(width + right.width)

        for ((r1, r2) in rows.zip(right.rows)) {
            result.println(r1 + divider + r2)
        }
        if (rows.size > right.rows.size) {
            rows.subList(right.rows.size, rows.size - padding).forEach {
                result.println(it + divider)
            }
        } else if (rows.size < right.rows.size) {
            right.rows.subList(rows.size, right.rows.size - padding).forEach {
                result.println("".padEnd(width) + divider + it)
            }
        }

        return result
    }

    override fun toString() = rows.joinToString("\n")
}

fun Column.renderFilters(qfResult: QueryFiltersResult) {
    var ix = 1
    for (filterResult in qfResult) {
        when (filterResult) {
            is FacetFilterResult<*> -> {
                renderFacetFilter(ix,  filterResult)
                ix++
            }
            is RangeFacetFilterResult<*> -> {
                renderRangeFacetFilter(ix, filterResult)
                ix++
            }
        }
    }
}

fun Column.renderFacetFilter(filterIx: Int, filter: FacetFilterResult<*>) {
    val title = FILTER_TITLES.getOrElse(filter.name) { filter.name.title() }
    println("$filterIx. $title")
    for ((ix, fv) in filter.withIndex()) {
        val selectedMark = if (fv.selected) 'x' else ' '
        println("  ${ix + 1}. [$selectedMark] ${fv.value} (${fv.count})")
    }
}

fun Column.renderRangeFacetFilter(filterIx: Int, filter: RangeFacetFilterResult<*>) {
    val title = FILTER_TITLES.getOrElse(filter.name) { filter.name.title() }
    println("$filterIx. $title")
    println("  ${filter.from ?: "-∞"} - ${filter.to ?: "∞"}")
}

fun Column.renderSortFilter(title: String, sort: SortFilterResult) {
    println("$title:")
    for ((ix, sortValue) in sort.values.withIndex()) {
        val selectedMark = if (sortValue.selected) 'x' else ' '
        println("  ${ix + 1}. [$selectedMark] ${sortValue.value}  ")
    }
}

fun Column.renderPage(page: PageFilterResult, sort: SortFilterResult) {
    renderSortFilter("Sort", sort)
    println()

    for (hit in page.hits) {
        val doc = hit.source as BikeDocSource
        val renderedPrice = " €${doc.price}"
        val renderedHit = "{ id=${doc.id.toString().padEnd(2)} } ${doc.brand} ${doc.model} ${doc.modelYear} "
        val hitPad = width - renderedPrice.length - padding * 2 - 1
        println("${renderedHit.padEnd(hitPad, '.')}${renderedPrice}")
    }

    println()
    println(
        (1 .. page.totalPages).joinToString(" ", prefix = "Pages: ") { p ->
            if (p == page.page) {
                "[$p]"
            } else {
                p.toString()
            }
        }
    )
}
