// import kotlinx.validation.sourceSets

apply {
    plugin("org.jetbrains.kotlin.multiplatform")
    plugin("application")
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

val Project.sourceSets: SourceSetContainer
    get() = extensions.getByType(JavaPluginExtension::class).sourceSets

tasks {
    val startScripts by getting(CreateStartScripts::class)

    val subFieldsMistakeStartScript by registering(CreateStartScripts::class) {
        applicationName = "subfields-mistake"
        mainClass.set("samples.document.subfields.mistake.MistakeKt")
        classpath = sourceSets.getByName("main").runtimeClasspath
        outputDir = startScripts.outputDir
    }
    startScripts.dependsOn(subFieldsMistakeStartScript)

    register<JavaExec>("runSubFieldsMistake") {
        mainClass.set(subFieldsMistakeStartScript.get().mainClass)
        classpath = subFieldsMistakeStartScript.get().classpath!!
    }
}
