package samples.qf

import dev.evo.elasticmagic.qf.FacetFilter
import dev.evo.elasticmagic.qf.FacetRangeFilter
import dev.evo.elasticmagic.qf.PageFilter
import dev.evo.elasticmagic.qf.QueryFilters
import dev.evo.elasticmagic.qf.SortFilter
import dev.evo.elasticmagic.qf.SortFilterValue

object BikeQueryFilters : QueryFilters() {
    val price by FacetRangeFilter(BikeDoc.price)
    val manufacturer by FacetFilter(BikeDoc.manufacturer)
    val kind by FacetFilter(BikeDoc.kind)
    val weight by FacetRangeFilter(BikeDoc.weight)

    val sort by SortFilter(
        SortFilterValue("price", listOf(BikeDoc.price)),
        SortFilterValue("-price", listOf(BikeDoc.price.desc())),
        SortFilterValue("weight", listOf(BikeDoc.weight)),
    )

    val page by PageFilter()
}
