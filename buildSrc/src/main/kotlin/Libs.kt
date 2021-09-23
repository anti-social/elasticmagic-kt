import org.gradle.api.JavaVersion

import java.util.Properties

object Versions {
    private val versionProps = readVersionProperties()

    val jvmTarget = JavaVersion.VERSION_11

    // Gradle plugins
    const val grgit = "4.1.0"
    const val detekt = "1.18.1"
    const val dokka = "1.5.0"
    const val mkdocs = "2.1.1"

    // Kotlin and libs
    val kotlin = versionProps["kotlin"]!!.toString()
    const val kotlinxSerialization = "1.1.0"
    const val kotlinxCoroutines = "1.4.3-native-mt"
    const val ktor = "1.5.2"

    // Other dependencies
    const val jackson = "2.12.2"

    // Testing
    const val kotest = "4.4.1"

    private fun readVersionProperties(): Properties {
        return Versions::class.java.getResourceAsStream("/elasticmagic/versions.properties").use { versions ->
            Properties().apply {
                load(versions)
            }
        }
    }
}

object Libs {
    fun kotest(flavor: String): String {
        return "io.kotest:kotest-$flavor:${Versions.kotest}"
    }

    fun kotlinSerialization(flavor: String): String {
        return "org.jetbrains.kotlinx:kotlinx-serialization-$flavor:${Versions.kotlinxSerialization}"
    }

    fun kotlinCoroutines(flavor: String): String {
        return "org.jetbrains.kotlinx:kotlinx-coroutines-$flavor:${Versions.kotlinxCoroutines}"
    }

    fun ktorClient(flavor: String): String {
        return "io.ktor:ktor-client-$flavor:${Versions.ktor}"
    }

    fun jackson(flavor: String): String {
        return "com.fasterxml.jackson.core:jackson-$flavor:${Versions.jackson}"
    }
}
