package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.list

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

import kotlin.test.Test

fun <T> Iterator<T>.get(): T {
    if (!hasNext()) {
        throw IllegalStateException("No more elements in the iterator")
    }
    return next()
}

class AttrFacetFilterTests : ElasticsearchTestBase() {
    override val indexName = "attr-facet-filter"

    interface AttributeValue {
        val attrId: Int
        val valueId: Int
        val indexValue: Long
            get() = encodeAttrWithValue(attrId, valueId)
    }

    enum class Manufacturer(override val valueId: Int) : AttributeValue {
        Samsung(0),
        Motorola(1),
        Google(2),
        Xiaomi(3);

        companion object {
            const val ATTR_ID = 1
        }

        override val attrId = Manufacturer.ATTR_ID
    }

    enum class Processor(override val valueId: Int) : AttributeValue {
        Snapdragon(0),
        Mediatek(1),
        Exinos(2),
        Kirin(3),
        Tensor(4);

        companion object {
            const val ATTR_ID = 2
        }

        override val attrId = Processor.ATTR_ID
    }

    class Ram(override val valueId: Int) : AttributeValue {
        companion object {
            const val ATTR_ID = 3
        }

        override val attrId = ATTR_ID
    }

    enum class Connectivity(override val valueId: Int) : AttributeValue {
        WIFI_802_11_AC(0),
        WIFI_802_11_AX(1),
        NFC(2),
        INFRARED(3);

        companion object {
            const val ATTR_ID = 4;
        }

        override val attrId = Connectivity.ATTR_ID
    }

    object ItemDoc : Document() {
        val model by text()
        val selectAttrs by long()
        val rangeAttrs by long()
    }

    object ItemQueryFilters : QueryFilters() {
        val selectAttrs by AttrFacetFilter(ItemDoc.selectAttrs, "attr")
    }

    @Test
    fun facet() = runTestWithSerdes {
        withFixtures(
            ItemDoc,
            listOf(
                DynDocSource {
                    it[ItemDoc.model] = "Galaxy Note 10"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Samsung.indexValue,
                        Processor.Exinos.indexValue,
                        Ram(8).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.NFC.indexValue,
                    )
                },
                DynDocSource {
                    it[ItemDoc.model] = "Galaxy Note 20"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Samsung.indexValue,
                        Processor.Exinos.indexValue,
                        Ram(8).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.WIFI_802_11_AX.indexValue,
                        Connectivity.NFC.indexValue,
                    )
                },
                DynDocSource {
                    it[ItemDoc.model] = "Galaxy S22"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Samsung.indexValue,
                        Processor.Snapdragon.indexValue,
                        Ram(12).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.WIFI_802_11_AX.indexValue,
                        Connectivity.NFC.indexValue,
                    )
                },
                DynDocSource {
                    it[ItemDoc.model] = "Edge 20 Pro"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Motorola.indexValue,
                        Processor.Snapdragon.indexValue,
                        Ram(12).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.WIFI_802_11_AX.indexValue,
                        Connectivity.NFC.indexValue,
                    )
                },
                DynDocSource {
                    it[ItemDoc.model] = "Pixel 6 Pro"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Google.indexValue,
                        Processor.Tensor.indexValue,
                        Ram(12).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.WIFI_802_11_AX.indexValue,
                        Connectivity.NFC.indexValue,
                    )
                },
                DynDocSource {
                    it[ItemDoc.model] = "Redmi Note 11"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Xiaomi.indexValue,
                        Processor.Snapdragon.indexValue,
                        Ram(6).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.NFC.indexValue,
                        Connectivity.INFRARED.indexValue,
                    )
                },
                DynDocSource {
                    it[ItemDoc.model] = "12X"
                    it[ItemDoc.selectAttrs.list()] = mutableListOf(
                        Manufacturer.Xiaomi.indexValue,
                        Processor.Snapdragon.indexValue,
                        Ram(8).indexValue,
                        Connectivity.WIFI_802_11_AC.indexValue,
                        Connectivity.WIFI_802_11_AX.indexValue,
                        Connectivity.NFC.indexValue,
                        Connectivity.INFRARED.indexValue,
                    )
                },
            ).mapIndexed { ix, doc ->
                // println("$ix: ${doc[ItemDoc.selectAttrs.list()]}")
                DocSourceAndMeta(IdActionMeta(ix.toString()), doc)
            },
            cleanup = false
        ) {
            var searchQuery = SearchQuery()
            searchQuery.execute(index).totalHits shouldBe 7

            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery, emptyMap()
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 7

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 4
                manufacturerFacet.values[0].value shouldBe Manufacturer.Samsung.valueId
                manufacturerFacet.values[0].count shouldBe 3
                manufacturerFacet.values[1].value shouldBe Manufacturer.Xiaomi.valueId
                manufacturerFacet.values[1].count shouldBe 2
                manufacturerFacet.values[2].value shouldBe Manufacturer.Motorola.valueId
                manufacturerFacet.values[2].count shouldBe 1
                manufacturerFacet.values[3].value shouldBe Manufacturer.Google.valueId
                manufacturerFacet.values[3].count shouldBe 1

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 3
                processorFacet.values[0].value shouldBe Processor.Snapdragon.valueId
                processorFacet.values[0].count shouldBe 4
                processorFacet.values[1].value shouldBe Processor.Exinos.valueId
                processorFacet.values[1].count shouldBe 2
                processorFacet.values[2].value shouldBe Processor.Tensor.valueId
                processorFacet.values[2].count shouldBe 1

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 3
                ramFacet.values[0].value shouldBe 8
                ramFacet.values[0].count shouldBe 3
                ramFacet.values[1].value shouldBe 12
                ramFacet.values[1].count shouldBe 3
                ramFacet.values[2].value shouldBe 6
                ramFacet.values[2].count shouldBe 1

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.values[0].value shouldBe Connectivity.WIFI_802_11_AC.valueId
                connectivityFacet.values[0].count shouldBe 7
                connectivityFacet.values[1].value shouldBe Connectivity.NFC.valueId
                connectivityFacet.values[1].count shouldBe 7
                connectivityFacet.values[2].value shouldBe Connectivity.WIFI_802_11_AX.valueId
                connectivityFacet.values[2].count shouldBe 5
                connectivityFacet.values[3].value shouldBe Connectivity.INFRARED.valueId
                connectivityFacet.values[3].count shouldBe 2
            }

            // println("===============================")
            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery, mapOf(listOf("attr", "1") to listOf("0"))
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 3

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 4
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 3)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Xiaomi.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Motorola.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Google.valueId, 1)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 2
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Exinos.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 2
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(8, 2)
                    values.get() shouldBe AttrFacetValue(12, 1)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 3
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 3)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 3)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2)
                }
            }

            // println("===============================")
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
                selectAttrsFilter.name shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 3
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Motorola.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Google.valueId, 1)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 1
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 2)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 2
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(8, 2)
                    values.get() shouldBe AttrFacetValue(12, 2)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 3
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 2)
                }
            }

            // println("===============================")
            searchQuery = SearchQuery()
            ItemQueryFilters.apply(
                searchQuery,
                mapOf(
                    listOf("attr", "4", "all") to listOf("1", "3"),
                )
            ).let { appliedFilters ->
                val searchResult = searchQuery.execute(index)
                searchResult.totalHits shouldBe 1

                val qfResult = appliedFilters.processResult(searchResult)
                val selectAttrsFilter = qfResult[ItemQueryFilters.selectAttrs]
                selectAttrsFilter.name shouldBe "attr"
                selectAttrsFilter.facets.size shouldBe 4

                val manufacturerFacet = selectAttrsFilter
                    .facets[Manufacturer.ATTR_ID]
                    .shouldNotBeNull()
                manufacturerFacet.values.size shouldBe 1
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Xiaomi.valueId, 1)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 1
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 1
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(8, 1)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Connectivity.INFRARED.valueId, 1)
                }
            }
        }
    }
}
