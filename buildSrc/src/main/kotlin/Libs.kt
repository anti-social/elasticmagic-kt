import org.gradle.api.JavaVersion

import java.util.Properties

object Versions {
    private val versionProps = readVersionProperties()

    val jvmTarget = JavaVersion.VERSION_11

    // Gradle plugins
    const val jacoco = "0.8.8"
    const val grgit = "4.1.1"
    const val detekt = "1.22.0-RC2"
    const val dokka = "1.7.20"
    const val mkdocs = "2.4.0"
    const val binaryCompatibilityValidator = "0.10.1"

    // Kotlin and libs
    val kotlin = versionProps["kotlin"]!!.toString()
    const val kotlinxSerialization = "1.3.3"
    const val kotlinxCoroutines = "1.6.3-native-mt"
    const val kotlinxDatetime = "0.3.3"
    const val ktor = "2.1.3"

    // Serialization dependencies
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

object Plugins {
    val binaryCompatibilityValidator = "org.jetbrains.kotlinx.binary-compatibility-validator"
}

object Libs {
    fun kotest(flavor: String): String {
        return "io.kotest:kotest-$flavor:${Versions.kotest}"
    }

    fun kotlinxLib(lib: String, version: String): String {
        return "org.jetbrains.kotlinx:kotlinx-$lib:$version"
    }

    fun kotlinxSerialization(flavor: String): String {
        return kotlinxLib("serialization-$flavor", Versions.kotlinxSerialization)
    }

    fun kotlinxCoroutines(flavor: String): String {
        return kotlinxLib("coroutines-$flavor", Versions.kotlinxCoroutines)
    }

    fun kotlinxDatetime(): String {
        return kotlinxLib("datetime", Versions.kotlinxDatetime)
    }

    fun ktorClient(flavor: String): String {
        return "io.ktor:ktor-client-$flavor:${Versions.ktor}"
    }

    fun jackson(flavor: String): String {
        return "com.fasterxml.jackson.core:jackson-$flavor:${Versions.jackson}"
    }

    fun jacksonDataformat(flavor: String): String {
        return "com.fasterxml.jackson.dataformat:jackson-dataformat-$flavor:${Versions.jackson}"
    }
}
