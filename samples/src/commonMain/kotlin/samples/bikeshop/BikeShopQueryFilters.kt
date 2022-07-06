package samples.bikeshop

import dev.evo.elasticmagic.qf.FacetFilter
import dev.evo.elasticmagic.qf.PageFilter
import dev.evo.elasticmagic.qf.QueryFilters
import dev.evo.elasticmagic.qf.FacetRangeFilter
import dev.evo.elasticmagic.qf.SortFilter
import dev.evo.elasticmagic.qf.SortFilterValue
import dev.evo.elasticmagic.query.Sort

object BikeShopQueryFilters : QueryFilters() {
    val price by FacetRangeFilter(BikeDoc.price)
    val kind by FacetFilter(BikeDoc.kind)
    val brand by FacetFilter(BikeDoc.brand)
    val matherial by FacetFilter(BikeDoc.frameMaterial)
    val wheelSize by FacetFilter(BikeDoc.wheelSize)
    val size by FacetFilter(BikeDoc.frameSize)
    val roadSize by FacetRangeFilter(BikeDoc.roadFrameSize)
    val weight by FacetRangeFilter(BikeDoc.weight)

    val sort by SortFilter(
        SortFilterValue("price", listOf(BikeDoc.price, BikeDoc.id)),
        SortFilterValue("-price", listOf(BikeDoc.price.desc(), BikeDoc.id.desc())),
        SortFilterValue("weight", listOf(BikeDoc.weight.asc(missing = Sort.Missing.Last), BikeDoc.id)),
    )

    val page by PageFilter(defaultPageSize = 5)
}

val FILTER_TITLES = mapOf(
    BikeShopQueryFilters.wheelSize.name to "Wheel Size (inches)",
    BikeShopQueryFilters.roadSize.name to "Road Frame Size",
)
