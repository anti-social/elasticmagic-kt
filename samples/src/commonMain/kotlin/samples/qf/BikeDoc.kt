package samples.qf

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.enum

enum class BikeKind {
    BMX, MTB, CITY, ROAD, CYCLOCROSS, GRAVEL, EBIKE;
}

object BikeDoc : Document() {
    val price by float()
    val manufacturer by keyword()
    val model by text()
    val kind by keyword().enum(BikeKind::name)
    val weight by float()
}
