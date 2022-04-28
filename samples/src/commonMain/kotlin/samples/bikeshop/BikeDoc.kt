package samples.bikeshop

import dev.evo.elasticmagic.doc.DocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.enum
import dev.evo.elasticmagic.query.ToValue

enum class BikeKind : ToValue<String> {
    BMX, MTB, CITY, ROAD, CYCLOCROSS, GRAVEL, EBIKE;

    override fun toValue() = name

    override fun toString(): String = name.title()
}

enum class BikeWheelSize : ToValue<String> {
    D26, D27_5, D28, D29;

    override fun toValue() = name.substring(1).replace('_', '.')

    override fun toString(): String = toValue()
}

enum class BikeFrameSize : ToValue<String> {
    XS, S, M, L, XL, XXL;

    override fun toValue() = name
}

enum class BikeMaterial : ToValue<String> {
    CARBON, ALUMINIUM, STEEL;

    override fun toValue() = name

    override fun toString(): String = name.title()
}

enum class BikeBrakeType : ToValue<String> {
    HYDRAULIC_DISC, MECHANIC_DISC, V_BRAKES;

    override fun toValue() = name

    override fun toString(): String {
        return name.replace('_', ' ').title()
    }
}

object BikeDoc : Document() {
    val id by int(index = false)

    val brand by keyword()
    val model by keyword()
    val modelYear by int()

    val price by float()
    val inStock by boolean()

    // Specification
    val kind by keyword().enum(BikeKind::name)
    val wheelSize by keyword().enum(BikeWheelSize::toValue)
    val frameSize by keyword().enum(BikeFrameSize::name)
    val roadFrameSize by int()
    val frameMaterial by keyword().enum(BikeMaterial::name)
    val forkMaterial by keyword().enum(BikeMaterial::name)
    val fullSuspension by boolean()
    val frontSpeeds by int()
    val rearSpeeds by int()
    val elctronicShifts by boolean()
    val weight by float()
}

class BikeDocSource : DocSource() {
    var id by BikeDoc.id

    var brand by BikeDoc.brand
    var model by BikeDoc.model
    var modelYear by BikeDoc.modelYear

    var price by BikeDoc.price
    var inStock by BikeDoc.inStock

    var kind by BikeDoc.kind
    var wheelSize by BikeDoc.wheelSize
    var frameSizes by BikeDoc.frameSize.required().list()
    var roadFrameSizes by BikeDoc.roadFrameSize.required().list()
    var frameMaterial by BikeDoc.frameMaterial
    var forkMaterial by BikeDoc.forkMaterial
    var fullSuspension by BikeDoc.fullSuspension
    var frontSpeeds by BikeDoc.frontSpeeds
    var rearSpeeds by BikeDoc.rearSpeeds
    var electronicShifts by BikeDoc.elctronicShifts
    var weight by BikeDoc.weight
}
