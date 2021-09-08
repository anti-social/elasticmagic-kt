import org.gradle.kotlin.dsl.`kotlin-dsl`

repositories {
   jcenter()
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
}
