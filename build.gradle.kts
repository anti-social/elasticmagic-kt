import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") apply false
}

allprojects {
    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
    }

    group = "dev.evo.elasticmagic"
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        jcenter()
    }
}

configureMultiplatform()

configure<KotlinMultiplatformExtension> {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(project(":elasticmagic-transport"))
            }
        }
    }
}

