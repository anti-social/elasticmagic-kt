package samples.bikeshop

import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.bulk.IndexAction

val fixtures = listOf(
    BikeDocSource().apply {
        brand = "Cube"
        model = "AMS Zero99 C:68X Race"
        modelYear = 2022
        price = 4_199F
        inStock = true
        kind = BikeKind.MTB
        wheelSize = BikeWheelSize.D29
        frameSizes = mutableListOf(
            BikeFrameSize.S, BikeFrameSize.M, BikeFrameSize.L, BikeFrameSize.XL
        )
        frameMaterial = BikeMaterial.CARBON
        fullSuspension = true
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = false
        weight = 10.7F
    },
    BikeDocSource().apply {
        brand = "Cube"
        model = "Reaction C:62 ONE"
        modelYear = 2022
        price = 1_499F
        inStock = true
        kind = BikeKind.MTB
        wheelSize = BikeWheelSize.D29
        frameSizes = mutableListOf(
            BikeFrameSize.M, BikeFrameSize.L,
        )
        frameMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = false
        weight = 11.5F
    },
    BikeDocSource().apply {
        brand = "Cube"
        model = "Agree C:62 SL"
        modelYear = 2022
        price = 4_199F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(56)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 12
        electronicShifts = true
        weight = 7.8F
    },
    BikeDocSource().apply {
        brand = "Cube"
        model = "Cross Race Pro"
        modelYear = 2022
        price = 1_599F
        inStock = true
        kind = BikeKind.CYCLOCROSS
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(50, 53, 58)
        frameMaterial = BikeMaterial.ALUMINIUM
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 11
        electronicShifts = false
        weight = 10.1F
    },
    BikeDocSource().apply {
        brand = "Cube"
        model = "Cross Race C:68X SLT"
        modelYear = 2022
        price = 4_199F
        inStock = true
        kind = BikeKind.CYCLOCROSS
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(53)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = true
        weight = 7.5F
    },
    BikeDocSource().apply {
        brand = "Cube"
        model = "Nuroad C:62 Pro"
        modelYear = 2022
        price = 2_199F
        inStock = true
        kind = BikeKind.GRAVEL
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(56, 58, 61)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 1
        rearSpeeds = 11
        electronicShifts = false
        weight = 9.4F
    },
    BikeDocSource().apply {
        brand = "Cube"
        model = "Cross Stereo 140 HPC SLT"
        modelYear = 2022
        price = 3_899F
        inStock = true
        kind = BikeKind.MTB
        wheelSize = BikeWheelSize.D27_5
        frameSizes = mutableListOf(BikeFrameSize.M)
        frameMaterial = BikeMaterial.CARBON
        fullSuspension = true
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = false
        weight = 12.9F
    },
    // https://www.bike24.com/p2531978.html
    BikeDocSource().apply {
        brand = "Bianchi"
        model = "ARIA Disc"
        modelYear = 2022
        price = 4_299F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(55, 57, 59)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 12
        electronicShifts = true
    },
    BikeDocSource().apply {
        brand = "Santa Cruz"
        model = "Nomad 5 C S"
        modelYear = 2022
        price = 6_299F
        inStock = true
        kind = BikeKind.MTB
        wheelSize = BikeWheelSize.D27_5
        frameSizes = mutableListOf(BikeFrameSize.L, BikeFrameSize.XL)
        frameMaterial = BikeMaterial.CARBON
        fullSuspension = true
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = false
        weight = 15.2F
    },
    BikeDocSource().apply {
        brand = "Cannondale"
        model = "SystemSix HI-MOD"
        modelYear = 2022
        price = 7_226.05F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(51, 56, 58, 62)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 12
        electronicShifts = true
        weight = null
    },
    BikeDocSource().apply {
        brand = "Cannondale"
        model = "CAAD13 Disc"
        modelYear = 2022
        price = 1_879F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(51, 56, 60)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 10
        electronicShifts = false
        weight = null
    },
    BikeDocSource().apply {
        brand = "Scott"
        model = "Addict 20"
        modelYear = 2022
        price = 2_570.17F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(54)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 11
        electronicShifts = false
        weight = 8.4F
    },
    // https://www.bike24.com/p2518785.html
    BikeDocSource().apply {
        brand = "Scott"
        model = "Addict RC Ultimate"
        modelYear = 2022
        price = 12_999F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(52)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 2
        rearSpeeds = 12
        electronicShifts = true
        weight = 6.7F
    },
    // https://www.bike24.com/p2519199.html
    BikeDocSource().apply {
        brand = "Scott"
        model = "Ransom 930"
        modelYear = 2022
        price = 3_299F
        inStock = true
        kind = BikeKind.MTB
        wheelSize = BikeWheelSize.D29
        frameSizes = mutableListOf(BikeFrameSize.M)
        frameMaterial = BikeMaterial.ALUMINIUM
        fullSuspension = true
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = false
        weight = 15.95F
    },
    // https://www.bike24.com/p2470033.html
    BikeDocSource().apply {
        brand = "BMC"
        model = "Roadmachine X One"
        modelYear = 2022
        price = 5_999F
        inStock = true
        kind = BikeKind.ROAD
        wheelSize = BikeWheelSize.D28
        roadFrameSizes = mutableListOf(51, 54, 56)
        frameMaterial = BikeMaterial.CARBON
        forkMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = true
        weight = 9.4F
    },
    BikeDocSource().apply {
        brand = "Radon"
        model = "Jealous 8.0"
        modelYear = 2022
        price = 2_599F
        inStock = true
        kind = BikeKind.MTB
        wheelSize = BikeWheelSize.D29
        frameSizes = mutableListOf(BikeFrameSize.S, BikeFrameSize.M)
        frameMaterial = BikeMaterial.CARBON
        fullSuspension = false
        frontSpeeds = 1
        rearSpeeds = 12
        electronicShifts = false
        weight = 9.95F
    },
)
    .withIndex()
    .map { (docId, doc) ->
        IndexAction(
            IdActionMeta(docId.toString()),
            doc.apply {
                id = docId
            }
        )
    }
