
rootProject.name = "elasticmagic"

include(
    "json",
    "transport"
)

project(":json").name = "elasticmagic-json"
project(":transport").name = "elasticmagic-transport"
