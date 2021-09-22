import org.gradle.kotlin.dsl.`kotlin-dsl`

repositories {
   mavenCentral()
   maven("https://plugins.gradle.org/m2/")
}

plugins {
   idea
   `kotlin-dsl`
}

idea {
   module {
      isDownloadJavadoc = false
      isDownloadSources = false
   }
}

dependencies {
   // TODO: How could we use single kotlin version?
   implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")
   implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}
