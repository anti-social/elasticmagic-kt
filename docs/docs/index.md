# Welcome to Elasticmagic

Elasticmagic implements advanced type awareness DSL for Kotlin to construct Elasticsearch queries.

!!! Warning
    The library is in very alpha status. API may change significantly at any time.
    Use it on your own risk

## Getting started

### Setup

Add following dependencies in your `build.gradle.kts` script:

!!! TODO
    At the moment artifacts are not yet published on maven central. Stay tuned   

```kotlin
repositories {
    mavenCentral()
}

val elasticmagicVersion = "0.0.1"
val ktorVersion = "1.5.2"

dependencies {
    // Elasticmagic core api
    implementation("dev.evo.elasticmagic:elasticmagic:$elasticmagicVersion")
    // Json serialization using kotlinx.serialization
    implementation("dev.evo.elasticmagic:elasticmagic-serde-serialization-json:$elasticmagicVersion")
    // Transport that uses ktor http client
    implementation("dev.evo.elasticmagic:elasticmagic-transport-ktor:$elasticmagicVersion")

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
}
```

### Usage

First you need to describe a document (represents a mapping in terms of Elasticsearch):

```kotlin
--8<-- "../samples/src/main/kotlin/samples/started/UserDoc.kt"
```

Now create `ElasticsearchCluster` object. It is an entry point for executing search queries:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/started/Cluster.kt"
```

Create our index if it does not exist or update the mapping otherwise:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/started/EnsureIndexExists.kt"
```

Describe document sources and index them: 

```kotlin
--8<-- "../samples/src/main/kotlin/samples/started/IndexDocs.kt"
```

And finally we can search our data:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/started/Main.kt"
```

You can find fully working example at [Getting Started](https://github.com/anti-social/elasticmagic-kt/tree/master/samples/src/main/kotlin/samples/started)