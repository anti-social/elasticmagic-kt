
rootProject.name = "elasticmagic"

include(
    "serde",
    "serde-json",
    "transport",
    "transport-ktor",
    "tests"

)

project(":serde").name = "elasticmagic-serde"
project(":serde-json").name = "elasticmagic-serde-json"
project(":transport").name = "elasticmagic-transport"
project(":transport-ktor").name = "elasticmagic-transport-ktor"
