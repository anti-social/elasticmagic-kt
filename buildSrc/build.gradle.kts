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

val kotlinVersion = "2.0.21"
val nexusPublishVersion = "2.0.0"

// See example at: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceSetOutput.html
val generatedResourcesDir = layout.buildDirectory
    .dir("generated-resources/main")
    .get()
    .getAsFile()
val generateVersions = tasks.register("generateVersions") {
   val versionsDir = generatedResourcesDir.resolve("elasticmagic")
   outputs.dir(versionsDir)

   doLast {
      versionsDir.resolve("versions.properties")
         .writeText("kotlin=$kotlinVersion")
   }
}

sourceSets {
   main {
      output.dir(mapOf("builtBy" to generateVersions), generatedResourcesDir)
   }
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
   implementation("io.github.gradle-nexus:publish-plugin:$nexusPublishVersion")
}
