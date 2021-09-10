
rootProject.name = "elasticmagic"

include(
    "serde",
    "serde-serialization-json",
    "serde-jackson-json",
    "transport",
    "transport-ktor",
    "integ-tests",
    "samples",
)

project(":serde").name = "elasticmagic-serde"
project(":serde-serialization-json").name = "elasticmagic-serde-serialization-json"
project(":serde-jackson-json").name = "elasticmagic-serde-jackson-json"
project(":transport").name = "elasticmagic-transport"
project(":transport-ktor").name = "elasticmagic-transport-ktor"
