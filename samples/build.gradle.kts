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
    targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java) {
        binaries.all {
            binaryOptions["memoryModel"] = "experimental"
        }
    }

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":elasticmagic"))
                implementation(project(":elasticmagic-query-filters"))
                implementation(project(":elasticmagic-serde-serialization-json"))
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

val Project.sourceSets: SourceSetContainer
    get() = extensions.getByType(JavaPluginExtension::class).sourceSets

tasks {
    val startScripts by getting(CreateStartScripts::class)

    val subFieldsMistakeStartScript by creating(CreateStartScripts::class) {
        applicationName = "subfields-mistake"
        mainClass.set("samples.document.subfields.mistake.MistakeKt")
        classpath = sourceSets.getByName("main").runtimeClasspath
        outputDir = startScripts.outputDir
    }
    startScripts.dependsOn(subFieldsMistakeStartScript)

    register<JavaExec>("runSubFieldsMistake") {
        mainClass.set(subFieldsMistakeStartScript.mainClass)
        classpath = subFieldsMistakeStartScript.classpath!!
    }
}
