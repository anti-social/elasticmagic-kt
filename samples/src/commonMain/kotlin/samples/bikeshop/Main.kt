package samples.bikeshop

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.Refresh
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.qf.FacetFilterResult
import dev.evo.elasticmagic.qf.MutableQueryFilterParams
import dev.evo.elasticmagic.qf.QueryFiltersResult
import dev.evo.elasticmagic.qf.FacetRangeFilterResult
import dev.evo.elasticmagic.qf.SortFilter
import kotlinx.coroutines.runBlocking

import samples.started.cluster

fun main() = runBlocking {
    val bikeShopIndex = cluster["elasticmagic-samples_bike-shop"]
    if (!cluster.indexExists(bikeShopIndex.name)) {
        cluster.createIndex(
            bikeShopIndex.name, BikeDoc,
            settings = Params("index.number_of_replicas" to 0)
        )
    } else {
        cluster.updateMapping(bikeShopIndex.name, BikeDoc)
    }
    bikeShopIndex.bulk(fixtures, refresh = Refresh.TRUE)

    println()
    println("=== Bike shop is opened ===")
    println()

    val qfParams = MutableQueryFilterParams()
    mainLoop@while (true) {
        println("Query filter params: $qfParams")
        val searchQuery = SearchQuery(::BikeDocSource)
        val appliedFilters = BikeShopQueryFilters.apply(searchQuery, qfParams)
        val searchResult = searchQuery.execute(bikeShopIndex)

        val qfResult = appliedFilters.processResult(searchResult)

        println()
        val filtersColumn = Column(30).apply {
            hr('=')
            println("Filters")
            hr('=')
            renderFilters(qfResult)
            println()
        }

        val page = qfResult[BikeShopQueryFilters.page]
        val resultsColumn = Column(70).apply {
            hr('=')
            println("Results: ${page.totalHits} bikes")
            hr('=')
            renderPage(page, qfResult[BikeShopQueryFilters.sort])
        }

        println(filtersColumn.sideBySide(resultsColumn))

        do {
            val cmdLine = readCmd()
            val cmdParts = cmdLine.split(' ', limit = 2)
            val cmdArg = cmdParts.getOrNull(1)
            val cmdRes = when (cmdParts.getOrElse(0) { "" }) {
                "" -> true
                "quit", "q" -> break@mainLoop
                "filter", "f" -> qfParams.processFacetFilterCmd(cmdArg, qfResult)
                "sort", "s" -> qfParams.processSortCmd(cmdArg)
                "page", "p" -> qfParams.processPageCmd(cmdArg)
                "clear", "c" -> {
                    qfParams.clear()
                    true
                }
                else -> {
                    println("Unknown command")
                    false
                }
            }
        } while (!cmdRes)
    }

    println()
    println("See you later!")
}

fun readCmd(): String {
    println()
    println("Available commands:")
    println("  quit, q - Exit from the bike shop")
    println("  filter, f - Toggle a filter. Examples: f 2 1, f 1 1000 - 2000, f 1 3000 -")
    println("  sort, s - Sort results. Example: s 3")
    println("  page, p - Choose a page. Example: p 2")
    println("  clear, c - Clear all filters")
    println()
    print("Please enter a command: ")
    return readln().also { println() }
}

fun MutableQueryFilterParams.processFacetFilterCmd(arg: String?, result: QueryFiltersResult): Boolean {
    if (arg == null) {
        println("Filter requires an argument")
        return false
    }

    val filters = result.filter { it is FacetFilterResult<*> || it is FacetRangeFilterResult<*> }

    val argParts = arg.split(" ", limit = 2)
    if (argParts.isEmpty()) {
        println("Missing filter argument")
        return false
    }
    val filterIx = argParts[0].toIntOrNull()
    if (filterIx == null) {
        println("Invalid filter index")
        return false
    }

    val filter = filters.getOrNull(filterIx - 1)
    if (filter == null) {
        println("Invalid filter argument: filter not found")
        return false
    }

    if (argParts.size == 1) {
        val iter = this.iterator()
        for ((key, _) in iter) {
            if (filter.name == key.first()) {
                iter.remove()
            }
        }
    } else {
        when (filter) {
            is FacetFilterResult<*> -> {
                val valueIx = argParts[1].toIntOrNull()
                if (valueIx == null) {
                    println("Invalid filter value index")
                    return false
                }
                val filterValue = filter.values.getOrNull(valueIx - 1)
                if (filterValue == null) {
                    println("Invalid filter argument: value not found")
                    return false
                }

                val value = filterValue.value
                val valueStr = if (value is ToValue<*>) {
                    value.toValue().toString()
                } else {
                    value.toString()
                }

                val paramValues = this.getOrPut(listOf(filter.name), ::mutableListOf)
                if (filterValue.selected) {
                    paramValues.remove(valueStr)
                } else {
                    paramValues.add(valueStr)
                }
            }
            is FacetRangeFilterResult<*> -> {
                val valueParts = argParts[1].split(" ", limit = 3)
                if (valueParts.isEmpty()) {
                    println("Invalid range filter value")
                    return false
                }
                if (valueParts[0] == "-") {
                    if (valueParts.size < 2) {
                        println("Invalid range filter value")
                        return false
                    }
                    val to = valueParts[1]
                    this[listOf(filter.name, "lte")] = mutableListOf(to)
                } else {
                    val from = valueParts[0]
                    this[listOf(filter.name, "gte")] = mutableListOf(from)
                    if (valueParts.size == 3) {
                        val to = valueParts[2]
                        this[listOf(filter.name, "lte")] = mutableListOf(to)
                    }
                }
            }
        }
    }

    // Clear page when any filter was changed
    this.remove(listOf(BikeShopQueryFilters.page.name))

    return true
}

fun MutableQueryFilterParams.processSortCmd(arg: String?): Boolean {
    if (arg == null) {
        println("Sort requires an argument")
        return false
    }

    val sortValueIx = arg.toIntOrNull()
    if (sortValueIx == null) {
        println("Invalid sort value argument")
        return false
    }

    val filter = BikeShopQueryFilters.sort.filter as SortFilter
    val sortValue = filter.values.getOrNull(sortValueIx - 1)
    if (sortValue == null) {
        println("Invalid sort value argument")
        return false
    }

    this[listOf(BikeShopQueryFilters.sort.name)] = mutableListOf(sortValue.value)

    // Clear page when sort was changed
    this.remove(listOf(BikeShopQueryFilters.page.name))

    return true
}

fun MutableQueryFilterParams.processPageCmd(arg: String?): Boolean {
    if (arg == null) {
        println("Page requires an argument")
        return false
    }

    val page = arg.toIntOrNull()
    if (page == null) {
        println("Invalid page value")
        return false
    }

    this[listOf(BikeShopQueryFilters.page.name)] = mutableListOf(page.toString())
    return true
}

fun String.title(): String {
    return lowercase().replaceFirstChar { it.titlecase() }
}
