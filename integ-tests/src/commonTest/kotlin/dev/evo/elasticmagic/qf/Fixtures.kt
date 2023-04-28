package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.list

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
        const val ATTR_ID = 4
    }

    override val attrId = Connectivity.ATTR_ID
}

class DisplaySize(override val value: Float) : AttributeRangeValue {
    companion object {
        const val ATTR_ID = 5
    }

    override val attrId = ATTR_ID
}

class BatteryCapacity(override val value: Float) : AttributeRangeValue {
    companion object {
        const val ATTR_ID = 6
    }

    override val attrId = ATTR_ID
}

class ExtensionSlot(override val value: Boolean) : AttributeBoolValue {
    companion object {
        const val ATTR_ID = 7
    }

    override val attrId = ATTR_ID
}

object ItemDoc : Document() {
    val model by text()
    val selectAttrs by long()
    val rangeAttrs by long()
    val boolAttrs by long()
}

val FIXTURES = listOf(
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