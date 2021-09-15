plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation(project(":elasticmagic-serde-serialization-json"))
    implementation(project(":elasticmagic-transport-ktor"))

    implementation(Libs.ktorClient("cio"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

application {
    mainClass.set("samples.started.MainKt")
}

tasks {
    val startScripts by getting(CreateStartScripts::class)
    val subFieldsMistakeStartScript by creating(CreateStartScripts::class) {
        applicationName = "subfields-mistake"
        mainClass.set("samples.document.subfields.mistake.MistakeKt")
        classpath = startScripts.classpath
        outputDir = startScripts.outputDir
    }
    startScripts.dependsOn(subFieldsMistakeStartScript)

    register<JavaExec>("runSubFieldsMistake") {
        mainClass.set(subFieldsMistakeStartScript.mainClass)
        classpath = subFieldsMistakeStartScript.classpath!!
    }
}

