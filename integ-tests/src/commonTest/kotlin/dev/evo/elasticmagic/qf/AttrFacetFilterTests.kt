package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

fun <T> Iterator<T>.get(): T {
    if (!hasNext()) {
        throw IllegalStateException("No more elements in the iterator")
    }
    return next()
}

class AttrFacetFilterTests : ElasticsearchTestBase() {
    override val indexName = "attr-facet-filter"

    object ItemQueryFilters : QueryFilters() {
        val selectAttrs by AttrFacetFilter(ItemDoc.selectAttrs, "attr")
        val rangeAttrs by AttrRangeFacetFilter(ItemDoc.rangeAttrs, "attr")
        val boolAttrs by AttrBoolFacetFilter(ItemDoc.boolAttrs, "attr")
    }

    @Test
    @Suppress("CyclomaticComplexMethod")
    fun facet() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            var searchQuery = SearchQuery()
            searchQuery.execute(index).totalHits shouldBe 8

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery, emptyMap()
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 8

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "selectAttrs"
                selectAttrsFilter.paramName shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 4
                manufacturerFacet.values[0].value shouldBe Manufacturer.Samsung.valueId
                manufacturerFacet.values[0].count shouldBe 3
                manufacturerFacet.values[1].value shouldBe Manufacturer.Xiaomi.valueId
                manufacturerFacet.values[1].count shouldBe 3
                manufacturerFacet.values[2].value shouldBe Manufacturer.Motorola.valueId
                manufacturerFacet.values[2].count shouldBe 1
                manufacturerFacet.values[3].value shouldBe Manufacturer.Google.valueId
                manufacturerFacet.values[3].count shouldBe 1

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 4
                processorFacet.values[0].value shouldBe Processor.Snapdragon.valueId
                processorFacet.values[0].count shouldBe 4
                processorFacet.values[1].value shouldBe Processor.Exinos.valueId
                processorFacet.values[1].count shouldBe 2
                processorFacet.values[2].value shouldBe Processor.Mediatek.valueId
                processorFacet.values[2].count shouldBe 1
                processorFacet.values[3].value shouldBe Processor.Tensor.valueId
                processorFacet.values[3].count shouldBe 1

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 3
                ramFacet.values[0].value shouldBe 8
                ramFacet.values[0].count shouldBe 4
                ramFacet.values[1].value shouldBe 12
                ramFacet.values[1].count shouldBe 3
                ramFacet.values[2].value shouldBe 6
                ramFacet.values[2].count shouldBe 1

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.values[0].value shouldBe Connectivity.WIFI_802_11_AC.valueId
                connectivityFacet.values[0].count shouldBe 8
                connectivityFacet.values[1].value shouldBe Connectivity.NFC.valueId
                connectivityFacet.values[1].count shouldBe 8
                connectivityFacet.values[2].value shouldBe Connectivity.WIFI_802_11_AX.valueId
                connectivityFacet.values[2].count shouldBe 6
                connectivityFacet.values[3].value shouldBe Connectivity.INFRARED.valueId
                connectivityFacet.values[3].count shouldBe 3

                val rangeAttrsFilter = qfResult[ItemQueryFilters.rangeAttrs]
                rangeAttrsFilter.name shouldBe "rangeAttrs"
                rangeAttrsFilter.paramName shouldBe "attr"
                rangeAttrsFilter.facets.size shouldBe 2

                rangeAttrsFilter
                    .facets[DisplaySize.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe DisplaySize.ATTR_ID
                        facet.count shouldBe 8
                        facet.min shouldBe 6.1F
                        facet.max shouldBe 6.7F
                    }

                rangeAttrsFilter
                    .facets[BatteryCapacity.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe BatteryCapacity.ATTR_ID
                        facet.count shouldBe 8
                        facet.min shouldBe 3500F
                        facet.max shouldBe 5003F
                    }

                val boolAttrsFilter = qfResult[ItemQueryFilters.boolAttrs]
                boolAttrsFilter.name shouldBe "boolAttrs"
                boolAttrsFilter.paramName shouldBe "attr"
                boolAttrsFilter.facets.size shouldBe 1

                boolAttrsFilter
                    .facets[ExtensionSlot.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe ExtensionSlot.ATTR_ID
                        facet.values[0].value.shouldBeFalse()
                        facet.values[0].count shouldBe 5
                        facet.values[1].value.shouldBeTrue()
                        facet.values[1].count shouldBe 3
                    }
            }

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery, mapOf(listOf("attr", Manufacturer.ATTR_ID.toString()) to listOf("0"))
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 3

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "selectAttrs"
                selectAttrsFilter.paramName shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 4
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 3, true)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Xiaomi.valueId, 3, false)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Motorola.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Google.valueId, 1, false)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 2
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Exinos.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1, false)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 2
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(8, 2, false)
                    values.get() shouldBe AttrFacetValue(12, 1, false)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 3
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 3, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 3, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2, false)
                }

                val rangeAttrsFilter = qfResult[ItemQueryFilters.rangeAttrs]
                rangeAttrsFilter.name shouldBe "rangeAttrs"
                rangeAttrsFilter.paramName shouldBe "attr"
                rangeAttrsFilter.facets.size shouldBe 2

                rangeAttrsFilter
                    .facets[DisplaySize.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe DisplaySize.ATTR_ID
                        facet.count shouldBe 3
                        facet.min shouldBe 6.1F
                        facet.max shouldBe 6.7F
                    }

                rangeAttrsFilter
                    .facets[BatteryCapacity.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe BatteryCapacity.ATTR_ID
                        facet.count shouldBe 3
                        facet.min shouldBe 3500F
                        facet.max shouldBe 4300F
                    }
            }

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery,
                mapOf(
                    listOf("attr", "1") to listOf("0", "1"),
                    listOf("attr", "3") to listOf("12"),
                )
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 2

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "selectAttrs"
                selectAttrsFilter.paramName shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 3
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 1, true)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Motorola.valueId, 1, true)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Google.valueId, 1, false)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 1
                processorFacet.iterator().get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 2, false)

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 2
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(8, 2, false)
                    values.get() shouldBe AttrFacetValue(12, 2, true)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 3
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 2, false)
                }

                val rangeAttrsFilter = qfResult[ItemQueryFilters.rangeAttrs]
                rangeAttrsFilter.name shouldBe "rangeAttrs"
                rangeAttrsFilter.paramName shouldBe "attr"
                rangeAttrsFilter.facets.size shouldBe 2

                rangeAttrsFilter
                    .facets[DisplaySize.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe DisplaySize.ATTR_ID
                        facet.count shouldBe 2
                        facet.min shouldBe 6.1F
                        facet.max shouldBe 6.7F
                    }

                rangeAttrsFilter
                    .facets[BatteryCapacity.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe BatteryCapacity.ATTR_ID
                        facet.count shouldBe 2
                        facet.min shouldBe 3700F
                        facet.max shouldBe 4500F
                    }
            }

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery,
                mapOf(
                    listOf("attr", "2") to listOf("5"),
                    listOf("attr", "5", "gte") to listOf("6.5"),
                )
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 0

                val qfResult = appliedFilters.processResult(searchResult)

                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "selectAttrs"
                selectAttrsFilter.paramName shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 1

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 4
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Processor.Mediatek.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Processor.Exinos.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Processor.Tensor.valueId, 1, false)
                    // values.get() shouldBe AttrFacetValue(5, 0, true)
                }

                val rangeAttrsFilter = qfResult[ItemQueryFilters.rangeAttrs]
                rangeAttrsFilter.name shouldBe "rangeAttrs"
                rangeAttrsFilter.paramName shouldBe "attr"
                rangeAttrsFilter.facets.size shouldBe 1

                rangeAttrsFilter
                    .facets[DisplaySize.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe DisplaySize.ATTR_ID
                        facet.count shouldBe 0
                        facet.min.shouldBeNull()
                        facet.max.shouldBeNull()
                    }
            }

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery,
                mapOf(
                    listOf("attr", "2") to listOf("0", "2", "4"),
                    listOf("attr", "5", "gte") to listOf("6.4"),
                    listOf("attr", "6", "gte") to listOf("3700"),
                    listOf("attr", "6", "lte") to listOf("5000"),
                )
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 3

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "selectAttrs"
                selectAttrsFilter.paramName shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 3
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Motorola.valueId, 1, false)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 3
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 2, true)
                    values.get() shouldBe AttrFacetValue(Processor.Mediatek.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Processor.Exinos.valueId, 1, true)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 3
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(6, 1, false)
                    values.get() shouldBe AttrFacetValue(8, 1, false)
                    values.get() shouldBe AttrFacetValue(12, 1, false)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 3, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 3, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.INFRARED.valueId, 1, false)
                }

                val rangeAttrsFilter = qfResult[ItemQueryFilters.rangeAttrs]
                rangeAttrsFilter.name shouldBe "rangeAttrs"
                rangeAttrsFilter.paramName shouldBe "attr"
                rangeAttrsFilter.facets.size shouldBe 2

                rangeAttrsFilter
                    .facets[DisplaySize.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe DisplaySize.ATTR_ID
                        facet.count shouldBe 5
                        facet.min shouldBe 6.1F
                        facet.max shouldBe 6.7F
                    }

                rangeAttrsFilter
                    .facets[BatteryCapacity.ATTR_ID]
                    .shouldNotBeNull()
                    .let { facet ->
                        facet.attrId shouldBe BatteryCapacity.ATTR_ID
                        facet.count shouldBe 4
                        facet.min shouldBe 4300F
                        facet.max shouldBe 5003F
                    }
            }

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery,
                mapOf(
                    listOf("attr", "4", "all") to listOf("1", "3"),
                )
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 2

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "selectAttrs"
                selectAttrsFilter.paramName shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 1
                manufacturerFacet.iterator().get() shouldBe AttrFacetValue(Manufacturer.Xiaomi.valueId, 2, false)

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 2
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1, false)
                    values.get() shouldBe AttrFacetValue(Processor.Mediatek.valueId, 1, false)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 1
                ramFacet.iterator().get() shouldBe AttrFacetValue(8, 2, false)

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 2, false)
                    values.get() shouldBe AttrFacetValue(Connectivity.INFRARED.valueId, 2, false)

                    val rangeAttrsFilter = qfResult[ItemQueryFilters.rangeAttrs]
                    rangeAttrsFilter.name shouldBe "rangeAttrs"
                    rangeAttrsFilter.paramName shouldBe "attr"
                    rangeAttrsFilter.facets.size shouldBe 2

                    rangeAttrsFilter
                        .facets[DisplaySize.ATTR_ID]
                        .shouldNotBeNull()
                        .let { facet ->
                            facet.attrId shouldBe DisplaySize.ATTR_ID
                            facet.count shouldBe 2
                            facet.min shouldBe 6.28F
                            facet.max shouldBe 6.67F
                        }

                    rangeAttrsFilter
                        .facets[BatteryCapacity.ATTR_ID]
                        .shouldNotBeNull()
                        .let { facet ->
                            facet.attrId shouldBe BatteryCapacity.ATTR_ID
                            facet.count shouldBe 2
                            facet.min shouldBe 4500F
                            facet.max shouldBe 5000F
                        }
                }
            }
        }
    }
}
