plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":"))
}

application {
    mainClass.set("dev.evo.elasticmagic.samples.GettingStartedMainKt")
}
