package samples.qf

import dev.evo.elasticmagic.doc.DynDocSource
import samples.started.cluster

suspend fun process() {
    val filtersResult = appliedFilters.processResult(searchQuery.execute(cluster["bike"]))

    val manufacturerFacet = filtersResult[BikeQueryFilters.manufacturer]
    println("Manufacturers:")
    for (manufacturer in manufacturerFacet) {
        val selectedMark = if (manufacturer.selected) "x" else " "
        println("  [$selectedMark] ${manufacturer.value} (${manufacturer.count})")
    }
    println()

    val kindFacet = filtersResult[BikeQueryFilters.kind]
    println("Kinds:")
    for (kind in kindFacet) {
        val selectedMark = if (kind.selected) "x" else " "
        println("  [$selectedMark] ${kind.value} (${kind.count})")
    }
    println()

    val page = filtersResult[BikeQueryFilters.page]
    println("Results:")
    for (hit in page) {
        val source = requireNotNull(hit.source) as DynDocSource

        println("  ${source[BikeDoc.manufacturer]} ${source[BikeDoc.model]} - ${source[BikeDoc.price]}")
    }
    println()
    println("Current page: ${page.page}")
    println("Total pages: ${page.totalPages}")
    println()
}