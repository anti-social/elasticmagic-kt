
rootProject.name = "elasticmagic"

include(
    "json",
    "transport",
    "ktor-transport"
)

project(":json").name = "elasticmagic-json"
project(":transport").name = "elasticmagic-transport"
project(":ktor-transport").name = "elasticmagic-ktor-transport"
