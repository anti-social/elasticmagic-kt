package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.list

import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
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

    interface AttributeRangeValue {
        val attrId: Int
        val value: Float
        val indexValue: Long
            get() = encodeRangeAttrWithValue(attrId, value)
    }

    interface AttributeBoolValue {
        val attrId: Int
        val value: Boolean
        val indexValue: Long
            get() = encodeBoolAttrWithValue(attrId, value)
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

    class DisplaySize(override val value: Float) : AttributeRangeValue {
        companion object {
            const val ATTR_ID = 5;
        }

        override val attrId = ATTR_ID
    }

    class BatteryCapacity(override val value: Float) : AttributeRangeValue {
        companion object {
            const val ATTR_ID = 6;
        }

        override val attrId = ATTR_ID
    }

    class ExtensionSlot(override val value: Boolean) : AttributeBoolValue {
        companion object {
            const val ATTR_ID = 7;
        }

        override val attrId = ATTR_ID
    }

    object ItemDoc : Document() {
        val model by text()
        val selectAttrs by long()
        val rangeAttrs by long()
        val boolAttrs by long()
    }

    object ItemQueryFilters : QueryFilters() {
        val selectAttrs by AttrFacetFilter(ItemDoc.selectAttrs, "attr")
        val rangeAttrs by AttrRangeFacetFilter(ItemDoc.rangeAttrs, "attr")
        val boolAttrs by AttrBoolFacetFilter(ItemDoc.boolAttrs, "attr")
    }

    companion object {
        private val FIXTURES = listOf(
            DynDocSource {
                it[ItemDoc.model] = "Galaxy Note 10"
                it[ItemDoc.selectAttrs.list()] = mutableListOf(
                    Manufacturer.Samsung.indexValue,
                    Processor.Exinos.indexValue,
                    Ram(8).indexValue,
                    Connectivity.WIFI_802_11_AC.indexValue,
                    Connectivity.NFC.indexValue,
                )
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.3F).indexValue,
                    BatteryCapacity(3500F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(false).indexValue,
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
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.7F).indexValue,
                    BatteryCapacity(4300F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(false).indexValue,
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
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.1F).indexValue,
                    BatteryCapacity(3700F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(false).indexValue,
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
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.7F).indexValue,
                    BatteryCapacity(4500F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(true).indexValue,
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
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.7F).indexValue,
                    BatteryCapacity(5003F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(false).indexValue,
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
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.43F).indexValue,
                    BatteryCapacity(5000F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(true).indexValue,
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
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.28F).indexValue,
                    BatteryCapacity(4500F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(true).indexValue,
                )
            },
            DynDocSource {
                it[ItemDoc.model] = "Redmi Note 12 Pro"
                it[ItemDoc.selectAttrs.list()] = mutableListOf(
                    Manufacturer.Xiaomi.indexValue,
                    Processor.Mediatek.indexValue,
                    Ram(8).indexValue,
                    Connectivity.WIFI_802_11_AC.indexValue,
                    Connectivity.WIFI_802_11_AX.indexValue,
                    Connectivity.NFC.indexValue,
                    Connectivity.INFRARED.indexValue,
                )
                it[ItemDoc.rangeAttrs.list()] = mutableListOf(
                    DisplaySize(6.67F).indexValue,
                    BatteryCapacity(5000F).indexValue,
                )
                it[ItemDoc.boolAttrs.list()] = mutableListOf(
                    ExtensionSlot(false).indexValue,
                )
            },
        ).mapIndexed { ix, doc ->
            DocSourceAndMeta(IdActionMeta(ix.toString()), doc)
        }
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
                searchQuery, mapOf(listOf("attr", "1") to listOf("0"))
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
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 3)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Xiaomi.valueId, 3)
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
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Processor.Mediatek.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Processor.Exinos.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Processor.Tensor.valueId, 1)
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
                    values.get() shouldBe AttrFacetValue(Manufacturer.Samsung.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Manufacturer.Motorola.valueId, 1)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 3
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Processor.Mediatek.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Processor.Exinos.valueId, 1)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 3
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(6, 1)
                    values.get() shouldBe AttrFacetValue(8, 1)
                    values.get() shouldBe AttrFacetValue(12, 1)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 3)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 3)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Connectivity.INFRARED.valueId, 1)
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
                manufacturerFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Manufacturer.Xiaomi.valueId, 2)
                }

                val processorFacet = selectAttrsFilter
                    .facets[Processor.ATTR_ID]
                    .shouldNotBeNull()
                processorFacet.values.size shouldBe 2
                processorFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Processor.Snapdragon.valueId, 1)
                    values.get() shouldBe AttrFacetValue(Processor.Mediatek.valueId, 1)
                }

                val ramFacet = selectAttrsFilter
                    .facets[Ram.ATTR_ID]
                    .shouldNotBeNull()
                ramFacet.values.size shouldBe 1
                ramFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(8, 2)
                }

                val connectivityFacet = selectAttrsFilter
                    .facets[Connectivity.ATTR_ID]
                    .shouldNotBeNull()
                connectivityFacet.values.size shouldBe 4
                connectivityFacet.iterator().let { values ->
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AC.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Connectivity.WIFI_802_11_AX.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Connectivity.NFC.valueId, 2)
                    values.get() shouldBe AttrFacetValue(Connectivity.INFRARED.valueId, 2)

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
