import org.gradle.api.JavaVersion

import java.util.Properties

object Versions {
    private val versionProps = readVersionProperties()

    // Gradle plugins
    const val jacoco = "0.8.12"
    const val grgit = "4.1.1"
    const val detekt = "1.23.7"
    const val dokka = "1.9.20"
    const val mkdocs = "4.0.1"
    // With 0.13.0 ':integ-tests:transformJvmTestAtomicfu' task somehow fails
    const val binaryCompatibilityValidator = "0.17.0"

    // Kotlin and libs
    val kotlin = versionProps["kotlin"]!!.toString()
    // At 1.5.0 it started to fall with JS
    const val kotlinxSerialization = "1.4.1"
    const val kotlinxCoroutines = "1.6.3-native-mt"
    const val kotlinxDatetime = "0.4.0"
    const val kotlinxAtomicfu = "0.20.0"
    const val kotlinxBenchmark = "0.4.13"
    // A lot of trace logging in JS client on 2.2.3 and later:
    // See: https://youtrack.jetbrains.com/issue/KTOR-5456/Ktor-JS-websocket-client-unconfigurable-logging
    const val ktor = "3.0.3"

    // Serialization dependencies
    const val jackson = "2.12.2"

    // Testing
    const val kotest = "5.5.5"

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
        return "org.jetbrains.kotlinx:$lib:$version"
    }

    fun kotlinxSerialization(flavor: String): String {
        return kotlinxLib("kotlinx-serialization-$flavor", Versions.kotlinxSerialization)
    }

    fun kotlinxCoroutines(flavor: String): String {
        return kotlinxLib("kotlinx-coroutines-$flavor", Versions.kotlinxCoroutines)
    }

    fun kotlinxDatetime(): String {
        return kotlinxLib("kotlinx-datetime", Versions.kotlinxDatetime)
    }

    fun kotlinxAtomicfu(): String {
        return kotlinxLib("atomicfu", Versions.kotlinxAtomicfu)
    }

    fun kotlinxAtomicfuGradlePlugin(): String {
        return kotlinxLib("atomicfu-gradle-plugin", Versions.kotlinxAtomicfu)
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
