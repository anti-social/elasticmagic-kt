object Versions {
    // Gradle plugins
    const val dokka = "1.5.0"

    // Kotlin and libs
    const val kotlin = "1.4.32"
    const val kotlinxSerialization = "1.1.0"
    const val kotlinxCoroutines = "1.4.3-native-mt"
    const val ktor = "1.5.2"

    // Other dependencies
    const val jackson = "2.12.2"

    // Testing
    const val kotest = "4.4.1"
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
