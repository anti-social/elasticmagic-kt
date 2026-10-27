// import kotlinx.validation.sourceSets

apply {
    plugin("org.jetbrains.kotlin.multiplatform")
}

repositories {
    mavenCentral()
}

configureMultiplatform(
    configureJs = false,
    entryPoints = listOf(
        "started",
        "bikeshop",
    )
)

configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":elasticmagic"))
                implementation(project(":elasticmagic-query-filters"))
                implementation(project(":elasticmagic-serde-kotlinx-json"))
                implementation(project(":elasticmagic-transport-ktor"))

                implementation(Libs.kotlinxCoroutines("core"))
            }
        }

        getByName("jvmMain") {
            dependencies {
                implementation(Libs.ktorClient("cio"))
            }
        }

        getByName("nativeMain") {
            dependencies {
                implementation(Libs.ktorClient("curl"))
            }
        }
    }
}

registerJvmRunTask("runSubFieldsMistake", "samples.document.subfields.mistake.MistakeKt")
