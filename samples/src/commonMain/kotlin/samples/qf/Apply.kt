package samples.qf

import dev.evo.elasticmagic.SearchQuery

// You can imagine it could be converted from following http query parameters:
// manufacturer=Giant&manufacturer=Cannondale&
// kind=CITY&kind=CYCLOCROSS&kind=GRAVEL&
// price__lte=2000&
// sort=weight&
// page=2&
val qfParams = mapOf(
    ("manufacturer" to "") to listOf("Giant", "Cannondale"),
    ("kind" to "") to listOf("CITY", "CYCLOCROSS", "GRAVEL"),
    ("price" to "lte") to listOf("2000"),
    ("sort" to "") to listOf("weight"),
    ("page" to "") to listOf("2"),
)

val searchQuery = SearchQuery()

val appliedFilters = BikeQueryFilters.apply(searchQuery, qfParams)

// Now searchQuery is filtered, sorted, paginated and corresponding aggregations
// to calculate facets are added
